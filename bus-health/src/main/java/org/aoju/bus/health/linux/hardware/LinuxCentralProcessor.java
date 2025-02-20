/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2023 aoju.org OSHI and other contributors.                 *
 *                                                                               *
 * Permission is hereby granted, free of charge, to any person obtaining a copy  *
 * of this software and associated documentation files (the "Software"), to deal *
 * in the Software without restriction, including without limitation the rights  *
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell     *
 * copies of the Software, and to permit persons to whom the Software is         *
 * furnished to do so, subject to the following conditions:                      *
 *                                                                               *
 * The above copyright notice and this permission notice shall be included in    *
 * all copies or substantial portions of the Software.                           *
 *                                                                               *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR    *
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,      *
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE   *
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER        *
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, *
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN     *
 * THE SOFTWARE.                                                                 *
 *                                                                               *
 ********************************************************************************/
package org.aoju.bus.health.linux.hardware;

import com.sun.jna.platform.linux.Udev;
import com.sun.jna.platform.linux.Udev.UdevContext;
import com.sun.jna.platform.linux.Udev.UdevDevice;
import com.sun.jna.platform.linux.Udev.UdevEnumerate;
import com.sun.jna.platform.linux.Udev.UdevListEntry;
import org.aoju.bus.core.annotation.ThreadSafe;
import org.aoju.bus.core.lang.RegEx;
import org.aoju.bus.core.lang.tuple.Quartet;
import org.aoju.bus.core.lang.tuple.Triple;
import org.aoju.bus.core.toolkit.StringKit;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Executor;
import org.aoju.bus.health.builtin.hardware.AbstractCentralProcessor;
import org.aoju.bus.health.builtin.hardware.CentralProcessor;
import org.aoju.bus.health.linux.LinuxLibc;
import org.aoju.bus.health.linux.ProcPath;
import org.aoju.bus.health.linux.drivers.Lshw;
import org.aoju.bus.health.linux.drivers.proc.CpuStat;
import org.aoju.bus.health.linux.software.LinuxOperatingSystem;
import org.aoju.bus.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * A CPU as defined in Linux /proc.
 *
 * @author Kimi Liu
 * @since Java 17+
 */
@ThreadSafe
final class LinuxCentralProcessor extends AbstractCentralProcessor {

    /**
     * Fetches the ProcessorID from dmidecode (if possible with root permissions),
     * the cpuid command (if installed) or by encoding the stepping, model, family,
     * and feature flags.
     *
     * @param vendor   The vendor
     * @param stepping The stepping
     * @param model    The model
     * @param family   The family
     * @param flags    The flags
     * @return The Processor ID string
     */
    private static String getProcessorID(String vendor, String stepping, String model, String family, String[] flags) {
        boolean procInfo = false;
        String marker = "Processor Information";
        for (String checkLine : Executor.runNative("dmidecode -t 4")) {
            if (!procInfo && checkLine.contains(marker)) {
                marker = "ID:";
                procInfo = true;
            } else if (procInfo && checkLine.contains(marker)) {
                return checkLine.split(marker)[1].trim();
            }
        }
        // If we've gotten this far, dmidecode failed. Try cpuid.
        marker = "eax=";
        for (String checkLine : Executor.runNative("cpuid -1r")) {
            if (checkLine.contains(marker) && checkLine.trim().startsWith("0x00000001")) {
                String eax = "";
                String edx = "";
                for (String register : RegEx.SPACES.split(checkLine)) {
                    if (register.startsWith("eax=")) {
                        eax = Builder.removeMatchingString(register, "eax=0x");
                    } else if (register.startsWith("edx=")) {
                        edx = Builder.removeMatchingString(register, "edx=0x");
                    }
                }
                return edx + eax;
            }
        }
        // If we've gotten this far, dmidecode failed. Encode arguments
        if (vendor.startsWith("0x")) {
            return createMIDR(vendor, stepping, model, family) + "00000000";
        }
        return createProcessorID(stepping, model, family, flags);
    }

    /**
     * Creates the MIDR, the ARM equivalent of CPUID ProcessorID
     *
     * @param vendor   the CPU implementer
     * @param stepping the "rnpn" variant and revision
     * @param model    the partnum
     * @param family   the architecture
     * @return A 32-bit hex string for the MIDR
     */
    private static String createMIDR(String vendor, String stepping, String model, String family) {
        int midrBytes = 0;
        // Build 32-bit MIDR
        if (stepping.startsWith("r") && stepping.contains("p")) {
            String[] rev = stepping.substring(1).split("p");
            // 3:0 – Revision: last n in rnpn
            midrBytes |= Builder.parseLastInt(rev[1], 0);
            // 23:20 - Variant: first n in rnpn
            midrBytes |= Builder.parseLastInt(rev[0], 0) << 20;
        }
        // 15:4 - PartNum = model
        midrBytes |= Builder.parseLastInt(model, 0) << 4;
        // 19:16 - Architecture = family
        midrBytes |= Builder.parseLastInt(family, 0) << 16;
        // 31:24 - Implementer = vendor
        midrBytes |= Builder.parseLastInt(vendor, 0) << 24;

        return String.format(Locale.ROOT, "%08X", midrBytes);
    }

    private static Quartet<List<LogicalProcessor>, List<ProcessorCache>, Map<Integer, Integer>, Map<Integer, String>> readTopologyFromUdev() {
        List<LogicalProcessor> logProcs = new ArrayList<>();
        Set<ProcessorCache> caches = new HashSet<>();
        Map<Integer, Integer> coreEfficiencyMap = new HashMap<>();
        Map<Integer, String> modAliasMap = new HashMap<>();
        // Enumerate CPU topology from sysfs via udev
        UdevContext udev = Udev.INSTANCE.udev_new();
        try {
            UdevEnumerate enumerate = udev.enumerateNew();
            try {
                enumerate.addMatchSubsystem("cpu");
                enumerate.scanDevices();
                for (UdevListEntry entry = enumerate.getListEntry(); entry != null; entry = entry.getNext()) {
                    String syspath = entry.getName(); // /sys/devices/system/cpu/cpuX
                    UdevDevice device = udev.deviceNewFromSyspath(syspath);
                    String modAlias = null;
                    if (device != null) {
                        try {
                            modAlias = device.getPropertyValue("MODALIAS");
                        } finally {
                            device.unref();
                        }
                    }
                    logProcs.add(
                            getLogicalProcessorFromSyspath(syspath, caches, modAlias, coreEfficiencyMap, modAliasMap));
                }
            } finally {
                enumerate.unref();
            }
        } finally {
            udev.unref();
        }
        return new Quartet<>(logProcs, orderedProcCaches(caches), coreEfficiencyMap, modAliasMap);
    }

    private static Quartet<List<LogicalProcessor>, List<ProcessorCache>, Map<Integer, Integer>, Map<Integer, String>> readTopologyFromSysfs() {
        List<LogicalProcessor> logProcs = new ArrayList<>();
        Set<ProcessorCache> caches = new HashSet<>();
        Map<Integer, Integer> coreEfficiencyMap = new HashMap<>();
        Map<Integer, String> modAliasMap = new HashMap<>();
        String cpuPath = "/sys/devices/system/cpu/";
        try {
            try (Stream<Path> cpuFiles = Files.find(Paths.get(cpuPath), Integer.MAX_VALUE,
                    (path, basicFileAttributes) -> path.toFile().getName().matches("cpu\\d+"))) {
                cpuFiles.forEach(cpu -> {
                    String syspath = cpu.toString(); // /sys/devices/system/cpu/cpuX
                    Map<String, String> uevent = Builder.getKeyValueMapFromFile(syspath + "/uevent", "=");
                    String modAlias = uevent.get("MODALIAS");
                    // updates caches as a side-effect
                    logProcs.add(
                            getLogicalProcessorFromSyspath(syspath, caches, modAlias, coreEfficiencyMap, modAliasMap));
                });
            }
        } catch (IOException e) {
            // No udev and no cpu info in sysfs? Bad.
            Logger.warn("Unable to find CPU information in sysfs at path {}", cpuPath);
        }
        return new Quartet<>(logProcs, orderedProcCaches(caches), coreEfficiencyMap, modAliasMap);
    }

    private static LogicalProcessor getLogicalProcessorFromSyspath(String syspath, Set<ProcessorCache> caches,
                                                                   String modAlias, Map<Integer, Integer> coreEfficiencyMap, Map<Integer, String> modAliasMap) {
        int processor = Builder.getFirstIntValue(syspath);
        int coreId = Builder.getIntFromFile(syspath + "/topology/core_id");
        int pkgId = Builder.getIntFromFile(syspath + "/topology/physical_package_id");
        int pkgCoreKey = (pkgId << 16) + coreId;
        // The cpu_capacity value may not exist, this will just store 0
        coreEfficiencyMap.put(pkgCoreKey, Builder.getIntFromFile(syspath + "/cpu_capacity"));
        if (!StringKit.isBlank(modAlias)) {
            modAliasMap.put(pkgCoreKey, modAlias);
        }
        int nodeId = 0;
        final String nodePrefix = syspath + "/node";
        try (Stream<Path> path = Files.list(Paths.get(syspath))) {
            Optional<Path> first = path.filter(p -> p.toString().startsWith(nodePrefix)).findFirst();
            if (first.isPresent()) {
                nodeId = Builder.getFirstIntValue(first.get().getFileName().toString());
            }
        } catch (IOException e) {
            // ignore
        }
        final String cachePath = syspath + "/cache";
        final String indexPrefix = cachePath + "/index";
        try (Stream<Path> path = Files.list(Paths.get(cachePath))) {
            path.filter(p -> p.toString().startsWith(indexPrefix)).forEach(c -> {
                int level = Builder.getIntFromFile(c + "/level"); // 1
                ProcessorCache.Type type = parseCacheType(Builder.getStringFromFile(c + "/type")); // Data
                int associativity = Builder.getIntFromFile(c + "/ways_of_associativity"); // 8
                int lineSize = Builder.getIntFromFile(c + "/coherency_line_size"); // 64
                long size = Builder.parseDecimalMemorySizeToBinary(Builder.getStringFromFile(c + "/size")); // 32K
                caches.add(new ProcessorCache(level, associativity, lineSize, size, type));
            });
        } catch (IOException e) {
            // ignore
        }
        return new LogicalProcessor(processor, coreId, pkgId, nodeId);
    }

    private static ProcessorCache.Type parseCacheType(String type) {
        try {
            return ProcessorCache.Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ProcessorCache.Type.UNIFIED;
        }
    }

    @Override
    protected CentralProcessor.ProcessorIdentifier queryProcessorId() {
        String cpuVendor = "";
        String cpuName = "";
        String cpuFamily = "";
        String cpuModel = "";
        String cpuStepping = "";
        String processorID;
        long cpuFreq = 0L;
        boolean cpu64bit = false;

        StringBuilder armStepping = new StringBuilder(); // For ARM equivalent
        String[] flags = new String[0];
        List<String> cpuInfo = Builder.readFile(ProcPath.CPUINFO);
        for (String line : cpuInfo) {
            String[] splitLine = Builder.whitespacesColonWhitespace.split(line);
            if (splitLine.length < 2) {
                // special case
                if (line.startsWith("CPU architecture: ")) {
                    cpuFamily = line.replace("CPU architecture: ", "").trim();
                }
                continue;
            }
            switch (splitLine[0].toLowerCase(Locale.ROOT)) {
                case "vendor_id":
                case "cpu implementer":
                    cpuVendor = splitLine[1];
                    break;
                case "model name":
                case "processor": // some ARM chips
                    // Ignore processor number
                    if (!splitLine[1].matches("[0-9]+")) {
                        cpuName = splitLine[1];
                    }
                    break;
                case "flags":
                    flags = splitLine[1].toLowerCase(Locale.ROOT).split(" ");
                    for (String flag : flags) {
                        if ("lm".equals(flag)) {
                            cpu64bit = true;
                            break;
                        }
                    }
                    break;
                case "stepping":
                    cpuStepping = splitLine[1];
                    break;
                case "CPU variant":
                    if (!armStepping.toString().startsWith("r")) {
                        // CPU variant format always starts with 0x
                        int rev = Builder.parseLastInt(splitLine[1], 0);
                        armStepping.insert(0, "r" + rev);
                    }
                    break;
                case "CPU revision":
                    if (!armStepping.toString().contains("p")) {
                        armStepping.append('p').append(splitLine[1]);
                    }
                    break;
                case "model":
                case "CPU part":
                    cpuModel = splitLine[1];
                    break;
                case "cpu family":
                    cpuFamily = splitLine[1];
                    break;
                case "cpu MHz":
                    cpuFreq = Builder.parseHertz(splitLine[1]);
                    break;
                default:
                    // Do nothing
            }
        }
        if (cpuName.isEmpty()) {
            cpuName = Builder.getStringFromFile(ProcPath.MODEL);
        }
        if (cpuName.contains("Hz")) {
            // if Name contains CPU vendor frequency, ignore cpuinfo and use it
            cpuFreq = -1L;
        } else {
            // Try lshw and use it in preference to cpuinfo
            long cpuCapacity = Lshw.queryCpuCapacity();
            if (cpuCapacity > cpuFreq) {
                cpuFreq = cpuCapacity;
            }
        }
        if (cpuStepping.isEmpty()) {
            cpuStepping = armStepping.toString();
        }
        processorID = getProcessorID(cpuVendor, cpuStepping, cpuModel, cpuFamily, flags);
        if (cpuVendor.startsWith("0x") || cpuModel.isEmpty()) {
            List<String> lscpu = Executor.runNative("lscpu");
            for (String line : lscpu) {
                if (line.startsWith("Architecture:") && cpuVendor.startsWith("0x")) {
                    cpuVendor = line.replace("Architecture:", "").trim();
                } else if (line.startsWith("Vendor ID:")) {
                    cpuVendor = line.replace("Vendor ID:", "").trim();
                } else if (line.startsWith("Model name:") && cpuModel.isEmpty()) {
                    cpuModel = line.replace("Model name:", "").trim();
                }
            }
        }
        return new CentralProcessor.ProcessorIdentifier(cpuVendor, cpuName, cpuFamily, cpuModel, cpuStepping, processorID, cpu64bit,
                cpuFreq);
    }

    @Override
    protected Triple<List<LogicalProcessor>, List<PhysicalProcessor>, List<ProcessorCache>> initProcessorCounts() {
        // Attempt to read from sysfs
        Quartet<List<LogicalProcessor>, List<ProcessorCache>, Map<Integer, Integer>, Map<Integer, String>> topology = LinuxOperatingSystem.HAS_UDEV
                ? readTopologyFromUdev()
                : readTopologyFromSysfs();
        // This sometimes fails so fall back to CPUID
        if (topology.getA().isEmpty()) {
            topology = readTopologyFromCpuinfo();
        }
        List<LogicalProcessor> logProcs = topology.getA();
        List<ProcessorCache> caches = topology.getB();
        Map<Integer, Integer> coreEfficiencyMap = topology.getC();
        Map<Integer, String> modAliasMap = topology.getD();
        // Failsafe
        if (logProcs.isEmpty()) {
            logProcs.add(new LogicalProcessor(0, 0, 0));
        }
        if (coreEfficiencyMap.isEmpty()) {
            coreEfficiencyMap.put(0, 0);
        }
        // Sort
        logProcs.sort(Comparator.comparingInt(LogicalProcessor::getProcessorNumber));

        List<PhysicalProcessor> physProcs = coreEfficiencyMap.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    int pkgId = e.getKey() >> 16;
                    int coreId = e.getKey() & 0xffff;
                    return new PhysicalProcessor(pkgId, coreId, e.getValue(), modAliasMap.getOrDefault(e.getKey(), ""));
                }).collect(Collectors.toList());

        return Triple.of(logProcs, physProcs, caches);
    }

    @Override
    public long[] querySystemCpuLoadTicks() {
        // convert the Linux Jiffies to Milliseconds.
        long[] ticks = CpuStat.getSystemCpuLoadTicks();
        // In rare cases, /proc/stat reading fails. If so, try again.
        if (LongStream.of(ticks).sum() == 0) {
            ticks = CpuStat.getSystemCpuLoadTicks();
        }
        long hz = LinuxOperatingSystem.getHz();
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = ticks[i] * 1000L / hz;
        }
        return ticks;
    }

    @Override
    public long[] queryCurrentFreq() {
        long[] freqs = new long[getLogicalProcessorCount()];
        // Attempt to fill array from cpu-freq source
        long max = 0L;
        UdevContext udev = Udev.INSTANCE.udev_new();
        try {
            UdevEnumerate enumerate = udev.enumerateNew();
            try {
                enumerate.addMatchSubsystem("cpu");
                enumerate.scanDevices();
                for (UdevListEntry entry = enumerate.getListEntry(); entry != null; entry = entry.getNext()) {
                    String syspath = entry.getName(); // /sys/devices/system/cpu/cpuX
                    int cpu = Builder.getFirstIntValue(syspath);
                    if (cpu >= 0 && cpu < freqs.length) {
                        freqs[cpu] = Builder.getLongFromFile(syspath + "/cpufreq/scaling_cur_freq");
                        if (freqs[cpu] == 0) {
                            freqs[cpu] = Builder.getLongFromFile(syspath + "/cpufreq/cpuinfo_cur_freq");
                        }
                    }
                    if (max < freqs[cpu]) {
                        max = freqs[cpu];
                    }
                }
                if (max > 0L) {
                    // If successful, array is filled with values in KHz.
                    for (int i = 0; i < freqs.length; i++) {
                        freqs[i] *= 1000L;
                    }
                    return freqs;
                }
            } finally {
                enumerate.unref();
            }
        } finally {
            udev.unref();
        }
        // If unsuccessful, try from /proc/cpuinfo
        Arrays.fill(freqs, -1);
        List<String> cpuInfo = Builder.readFile(ProcPath.CPUINFO);
        int proc = 0;
        for (String s : cpuInfo) {
            if (s.toLowerCase(Locale.ROOT).contains("cpu mhz")) {
                freqs[proc] = Math.round(Builder.parseLastDouble(s, 0d) * 1_000_000d);
                if (++proc >= freqs.length) {
                    break;
                }
            }
        }
        return freqs;
    }

    @Override
    public long queryMaxFreq() {
        long max = Arrays.stream(this.getCurrentFreq()).max().orElse(-1L);
        // Max of current freq, if populated, is in units of Hz, convert to kHz
        if (max > 0) {
            max /= 1000L;
        }
        // Iterating CPUs only gets the existing policy, so we need to iterate the
        // policy directories to find the system-wide policy max
        UdevContext udev = Udev.INSTANCE.udev_new();
        try {
            UdevEnumerate enumerate = udev.enumerateNew();
            try {
                enumerate.addMatchSubsystem("cpu");
                enumerate.scanDevices();
                // Find the parent directory of cpuX paths
                // We only need the first one of the iteration
                UdevListEntry entry = enumerate.getListEntry();
                if (entry != null) {
                    String syspath = entry.getName(); // /sys/devices/system/cpu/cpu0
                    String cpuFreqPath = syspath.substring(0, syspath.lastIndexOf(File.separatorChar)) + "/cpuFreq";
                    String policyPrefix = cpuFreqPath + "/policy";
                    try (Stream<Path> path = Files.list(Paths.get(cpuFreqPath))) {
                        Optional<Long> maxPolicy = path.filter(p -> p.toString().startsWith(policyPrefix)).map(p -> {
                            long freq = Builder.getLongFromFile(p + "/scaling_max_freq");
                            if (freq == 0) {
                                freq = Builder.getLongFromFile(p + "/cpuinfo_max_freq");
                            }
                            return freq;
                        }).max(Long::compare);
                        if (maxPolicy.isPresent() && max < maxPolicy.get()) {
                            max = maxPolicy.get();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            } finally {
                enumerate.unref();
            }
        } finally {
            udev.unref();
        }
        if (max == 0L) {
            return -1L;
        }
        // If successful, value is in KHz.
        max *= 1000L;
        // Cpufreq result assumes intel pstates and is unreliable for AMD processors.
        // Check lshw as a backup
        long lshwMax = Lshw.queryCpuCapacity();
        return lshwMax > max ? lshwMax : max;
    }

    @Override
    public double[] getSystemLoadAverage(int nelem) {
        if (nelem < 1 || nelem > 3) {
            throw new IllegalArgumentException("Must include from one to three elements.");
        }
        double[] average = new double[nelem];
        int retval = LinuxLibc.INSTANCE.getloadavg(average, nelem);
        if (retval < nelem) {
            for (int i = Math.max(retval, 0); i < average.length; i++) {
                average[i] = -1d;
            }
        }
        return average;
    }

    @Override
    public long[][] queryProcessorCpuLoadTicks() {
        long[][] ticks = CpuStat.getProcessorCpuLoadTicks(getLogicalProcessorCount());
        // In rare cases, /proc/stat reading fails. If so, try again.
        // In theory we should check all of them, but on failure we can expect all 0's
        // so we only need to check for processor 0
        if (LongStream.of(ticks[0]).sum() == 0) {
            ticks = CpuStat.getProcessorCpuLoadTicks(getLogicalProcessorCount());
        }
        // convert the Linux Jiffies to Milliseconds.
        long hz = LinuxOperatingSystem.getHz();
        for (int i = 0; i < ticks.length; i++) {
            for (int j = 0; j < ticks[i].length; j++) {
                ticks[i][j] = ticks[i][j] * 1000L / hz;
            }
        }
        return ticks;
    }

    @Override
    public long queryContextSwitches() {
        return CpuStat.getContextSwitches();
    }

    @Override
    public long queryInterrupts() {
        return CpuStat.getInterrupts();
    }


    private static Quartet<List<LogicalProcessor>, List<ProcessorCache>, Map<Integer, Integer>, Map<Integer, String>> readTopologyFromCpuinfo() {
        List<LogicalProcessor> logProcs = new ArrayList<>();
        Set<ProcessorCache> caches = mapCachesFromLscpu();
        Map<Integer, Integer> numaNodeMap = mapNumaNodesFromLscpu();
        Map<Integer, Integer> coreEfficiencyMap = new HashMap<>();

        List<String> procCpu = Builder.readFile(ProcPath.CPUINFO);
        int currentProcessor = 0;
        int currentCore = 0;
        int currentPackage = 0;

        boolean first = true;
        for (String cpu : procCpu) {
            // Count logical processors
            if (cpu.startsWith("processor")) {
                if (first) {
                    first = false;
                } else {
                    // add from the previous iteration
                    logProcs.add(new LogicalProcessor(currentProcessor, currentCore, currentPackage,
                            numaNodeMap.getOrDefault(currentProcessor, 0)));
                    // Count unique combinations of core id and physical id.
                    coreEfficiencyMap.put((currentPackage << 16) + currentCore, 0);
                }
                // start creating for this iteration
                currentProcessor = Builder.parseLastInt(cpu, 0);
            } else if (cpu.startsWith("core id") || cpu.startsWith("cpu number")) {
                currentCore = Builder.parseLastInt(cpu, 0);
            } else if (cpu.startsWith("physical id")) {
                currentPackage = Builder.parseLastInt(cpu, 0);
            }
        }
        logProcs.add(new LogicalProcessor(currentProcessor, currentCore, currentPackage,
                numaNodeMap.getOrDefault(currentProcessor, 0)));
        coreEfficiencyMap.put((currentPackage << 16) + currentCore, 0);
        return new Quartet<>(logProcs, orderedProcCaches(caches), coreEfficiencyMap, Collections.emptyMap());
    }

    private static Map<Integer, Integer> mapNumaNodesFromLscpu() {
        Map<Integer, Integer> numaNodeMap = new HashMap<>();
        // Get numa node info from lscpu
        List<String> lscpu = Executor.runNative("lscpu -p=cpu,node");
        // Format:
        // # comment lines starting with #
        // # then comma-delimited cpu,node
        // 0,0
        // 1,0
        for (String line : lscpu) {
            if (!line.startsWith("#")) {
                int pos = line.indexOf(',');
                if (pos > 0 && pos < line.length()) {
                    numaNodeMap.put(Builder.parseIntOrDefault(line.substring(0, pos), 0),
                            Builder.parseIntOrDefault(line.substring(pos + 1), 0));
                }
            }
        }
        return numaNodeMap;
    }

    private static Set<ProcessorCache> mapCachesFromLscpu() {
        Set<ProcessorCache> caches = new HashSet<>();
        int level = 0;
        ProcessorCache.Type type = null;
        int associativity = 0;
        int lineSize = 0;
        long size = 0L;
        // Get numa node info from lscpu
        List<String> lscpu = Executor.runNative("lscpu -B -C --json");
        for (String line : lscpu) {
            String s = line.trim();
            if (s.startsWith("}")) {
                // done with this entry, save it
                if (level > 0 && type != null) {
                    caches.add(new ProcessorCache(level, associativity, lineSize, size, type));
                }
                level = 0;
                type = null;
                associativity = 0;
                lineSize = 0;
                size = 0L;
            } else if (s.contains("one-size")) {
                // "one-size": "65536",
                String[] split = Builder.notDigits.split(s);
                if (split.length > 1) {
                    size = Builder.parseLongOrDefault(split[1], 0L);
                }
            } else if (s.contains("ways")) {
                // "ways": null,
                // "ways": 4,
                String[] split = Builder.notDigits.split(s);
                if (split.length > 1) {
                    associativity = Builder.parseIntOrDefault(split[1], 0);
                }
            } else if (s.contains("type")) {
                // "type": "Unified",
                String[] split = s.split("\"");
                if (split.length > 2) {
                    type = parseCacheType(split[split.length - 2]);
                }
            } else if (s.contains("level")) {
                // "level": 3,
                String[] split = Builder.notDigits.split(s);
                if (split.length > 1) {
                    level = Builder.parseIntOrDefault(split[1], 0);
                }
            } else if (s.contains("coherency-size")) {
                // "coherency-size": 64
                String[] split = Builder.notDigits.split(s);
                if (split.length > 1) {
                    lineSize = Builder.parseIntOrDefault(split[1], 0);
                }
            }
        }
        return caches;
    }


}
