/*********************************************************************************
 *                                                                               *
 * The MIT License (MIT)                                                         *
 *                                                                               *
 * Copyright (c) 2015-2021 aoju.org OSHI and other contributors.                 *
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
package org.aoju.bus.health.unix.openbsd.software;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.aoju.bus.core.annotation.ThreadSafe;
import org.aoju.bus.core.lang.RegEx;
import org.aoju.bus.health.Builder;
import org.aoju.bus.health.Executor;
import org.aoju.bus.health.Memoize;
import org.aoju.bus.health.builtin.software.AbstractOSProcess;
import org.aoju.bus.health.builtin.software.OSProcess;
import org.aoju.bus.health.builtin.software.OSThread;
import org.aoju.bus.health.unix.openbsd.FstatKit;
import org.aoju.bus.health.unix.openbsd.OpenBsdLibc;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * OSProcess implemenation
 *
 * @author Kimi Liu
 * @version 6.2.0
 * @since JDK 1.8+
 */
@ThreadSafe
public class OpenBsdOSProcess extends AbstractOSProcess {

    private final Supplier<Integer> bitness = Memoize.memoize(this::queryBitness);

    private String name;
    private String path = "";
    private String commandLine;
    private String user;
    private String userID;
    private String group;
    private String groupID;
    private State state = OSProcess.State.INVALID;
    private int parentProcessID;
    private int threadCount;
    private int priority;
    private long virtualSize;
    private long residentSetSize;
    private long kernelTime;
    private long userTime;
    private long startTime;
    private long upTime;
    private long bytesRead;
    private long bytesWritten;
    private long minorFaults;
    private long majorFaults;

    public OpenBsdOSProcess(int pid, String[] split) {
        super(pid);
        updateThreadCount();
        updateAttributes(split);
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public String getCommandLine() {
        return this.commandLine;
    }

    @Override
    public String getCurrentWorkingDirectory() {
        return FstatKit.getCwd(getProcessID());
    }

    @Override
    public String getUser() {
        return this.user;
    }

    @Override
    public String getUserID() {
        return this.userID;
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public String getGroupID() {
        return this.groupID;
    }

    @Override
    public State getState() {
        return this.state;
    }

    @Override
    public int getParentProcessID() {
        return this.parentProcessID;
    }

    @Override
    public int getThreadCount() {
        return this.threadCount;
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public long getVirtualSize() {
        return this.virtualSize;
    }

    @Override
    public long getResidentSetSize() {
        return this.residentSetSize;
    }

    @Override
    public long getKernelTime() {
        return this.kernelTime;
    }

    @Override
    public long getUserTime() {
        return this.userTime;
    }

    @Override
    public long getUpTime() {
        return this.upTime;
    }

    @Override
    public long getStartTime() {
        return this.startTime;
    }

    @Override
    public long getBytesRead() {
        return this.bytesRead;
    }

    @Override
    public long getBytesWritten() {
        return this.bytesWritten;
    }

    @Override
    public long getOpenFiles() {
        return FstatKit.getOpenFiles(getProcessID());
    }

    @Override
    public int getBitness() {
        return this.bitness.get();
    }

    @Override
    public long getAffinityMask() {
        long bitMask = 0L;
        // Would prefer to use native cpuset_getaffinity call but variable sizing is
        // kernel-dependent and requires C macros, so we use commandline instead.
        String cpuset = Executor.getFirstAnswer("cpuset -gp " + getProcessID());
        // Sample output:
        // pid 8 mask: 0, 1
        // cpuset: getaffinity: No such process
        String[] split = cpuset.split(":");
        if (split.length > 1) {
            String[] bits = split[1].split(",");
            for (String bit : bits) {
                int bitToSet = Builder.parseIntOrDefault(bit.trim(), -1);
                if (bitToSet >= 0) {
                    bitMask |= 1L << bitToSet;
                }
            }
        }
        return bitMask;
    }

    private int queryBitness() {
        // Get process abi vector
        int[] mib = new int[4];
        mib[0] = 1; // CTL_KERN
        mib[1] = 14; // KERN_PROC
        mib[2] = 9; // KERN_PROC_SV_NAME
        mib[3] = getProcessID();
        // Allocate memory for arguments
        Pointer abi = new Memory(32);
        IntByReference size = new IntByReference(32);
        // Fetch abi vector
        if (0 == OpenBsdLibc.INSTANCE.sysctl(mib, mib.length, abi, size, null, 0)) {
            String elf = abi.getString(0);
            if (elf.contains("ELF32")) {
                return 32;
            } else if (elf.contains("ELF64")) {
                return 64;
            }
        }
        return 0;
    }

    @Override
    public List<OSThread> getThreadDetails() {
        List<OSThread> threads = new ArrayList<>();
        // tdname, systime and tdaddr are unknown to OpenBSD ps
        // command may give the thread name, but should be put last with fixed length
        // split to avoid parsing any spaces
        String psCommand = "ps -aHwwxo tid,state,etime,time,nivcsw,nvcsw,majflt,minflt,pri,args";
        if (getProcessID() >= 0) {
            psCommand += " -p " + getProcessID();
        }
        List<String> threadList = Executor.runNative(psCommand);
        if (threadList.isEmpty() || threadList.size() < 2) {
            return threads;
        }
        // remove header row
        threadList.remove(0);
        // Fill list
        for (String thread : threadList) {
            String[] split = RegEx.SPACES.split(thread.trim(), 10);
            // Elements should match ps command order
            if (split.length == 10) {
                threads.add(new OpenBsdOSThread(getProcessID(), split));
            }
        }
        return threads;
    }

    @Override
    public long getMinorFaults() {
        return this.minorFaults;
    }

    @Override
    public long getMajorFaults() {
        return this.majorFaults;
    }

    @Override
    public boolean updateAttributes() {
        // 'ps' does not provide threadCount or kernelTime on OpenBSD
        String psCommand = "ps -awwxo state,pid,ppid,user,uid,group,gid,pri,vsz,rss,etime,cputime,comm,majflt,minflt,args -p "
                + getProcessID();
        List<String> procList = Executor.runNative(psCommand);
        if (procList.size() > 1) {
            // skip header row
            String[] split = RegEx.SPACES.split(procList.get(1).trim(), 16);
            if (split.length == 16) {
                updateThreadCount();
                return updateAttributes(split);
            }
        }
        this.state = OSProcess.State.INVALID;
        return false;
    }

    private boolean updateAttributes(String[] split) {
        long now = System.currentTimeMillis();
        switch (split[0].charAt(0)) {
            case 'R':
                this.state = OSProcess.State.RUNNING;
                break;
            case 'I':
            case 'S':
                this.state = OSProcess.State.SLEEPING;
                break;
            case 'D':
            case 'L':
            case 'U':
                this.state = OSProcess.State.WAITING;
                break;
            case 'Z':
                this.state = OSProcess.State.ZOMBIE;
                break;
            case 'T':
                this.state = OSProcess.State.STOPPED;
                break;
            default:
                this.state = OSProcess.State.OTHER;
                break;
        }
        this.parentProcessID = Builder.parseIntOrDefault(split[2], 0);
        this.user = split[3];
        this.userID = split[4];
        this.group = split[5];
        this.groupID = split[6];
        this.priority = Builder.parseIntOrDefault(split[7], 0);
        // These are in KB, multiply
        this.virtualSize = Builder.parseLongOrDefault(split[8], 0) * 1024;
        this.residentSetSize = Builder.parseLongOrDefault(split[9], 0) * 1024;
        // Avoid divide by zero for processes up less than a second
        long elapsedTime = Builder.parseDHMSOrDefault(split[10], 0L);
        this.upTime = elapsedTime < 1L ? 1L : elapsedTime;
        this.startTime = now - this.upTime;
        this.userTime = Builder.parseDHMSOrDefault(split[11], 0L);
        this.path = split[12];
        this.name = this.path.substring(this.path.lastIndexOf('/') + 1);
        this.minorFaults = Builder.parseLongOrDefault(split[13], 0L);
        this.majorFaults = Builder.parseLongOrDefault(split[14], 0L);
        this.commandLine = split[15];
        // kernel time is included in user time
        // this.kernelTime = Builder.parseDHMSOrDefault(split[16], 0L);
        this.kernelTime = 0L;
        return true;
    }

    private void updateThreadCount() {
        List<String> threadList = Executor.runNative("ps -axHo tid -p " + getProcessID());
        if (!threadList.isEmpty()) {
            // Subtract 1 for header
            this.threadCount = threadList.size() - 1;
        }
        this.threadCount = 1;
    }

}
