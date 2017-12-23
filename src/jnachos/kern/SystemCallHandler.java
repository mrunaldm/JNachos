/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *  
 *  Created by Patrick McSweeney on 12/5/08.
 */
package jnachos.kern;

import jnachos.filesystem.OpenFile;
import jnachos.machine.*;

import javax.crypto.Mac;
import java.awt.*;
import java.util.LinkedList;

/** The class handles System calls made from user programs. */
public class SystemCallHandler {
	/** The System call index for halting. */
	public static final int SC_Halt = 0;

	/** The System call index for exiting a program. */
	public static final int SC_Exit = 1;

	/** The System call index for executing program. */
	public static final int SC_Exec = 2;

	/** The System call index for joining with a process. */
	public static final int SC_Join = 3;

	/** The System call index for creating a file. */
	public static final int SC_Create = 4;

	/** The System call index for opening a file. */
	public static final int SC_Open = 5;

	/** The System call index for reading a file. */
	public static final int SC_Read = 6;

	/** The System call index for writting a file. */
	public static final int SC_Write = 7;

	/** The System call index for closing a file. */
	public static final int SC_Close = 8;

	/** The System call index for forking a forking a new process. */
	public static final int SC_Fork = 9;

	/** The System call index for yielding a program. */
	public static final int SC_Yield = 10;

	/**
	 * Entry point into the Nachos kernel. Called when a user program is
	 * executing, and either does a syscall, or generates an addressing or
	 * arithmetic exception.
	 * 
	 * For system calls, the following is the calling convention:
	 * 
	 * system call code -- r2 arg1 -- r4 arg2 -- r5 arg3 -- r6 arg4 -- r7
	 * 
	 * The result of the system call, if any, must be put back into r2.
	 * 
	 * And don't forget to increment the pc before returning. (Or else you'll
	 * loop making the same system call forever!
	 * 
	 * @pWhich is the kind of exception. The list of possible exceptions are in
	 *         Machine.java
	 **/
	public static void handleSystemCall(int pWhichSysCall) {
		boolean oldLevel;
		Debug.print('a', "!!!!" + Machine.read1 + "," + Machine.read2 + "," + Machine.read4 + "," + Machine.write1 + ","
				+ Machine.write2 + "," + Machine.write4);

		Machine.writeRegister(Machine.PCReg,Machine.readRegister(Machine.NextPCReg));

		Machine.writeRegister(Machine.NextPCReg,Machine.readRegister(Machine.NextPCReg)+4);

		switch (pWhichSysCall) {
		// If halt is received shut down
		case SC_Halt:
			Debug.print('a', "Shutdown, initiated by user program.");
			Interrupt.halt();
			break;

		case SC_Exit:
			// Read in any arguments from the 4th register
			NachosProcess finishingProcess = JNachos.getCurrentProcess();
			System.out.println("Exit system call SysCall("+pWhichSysCall+") called by process PID :"+finishingProcess.getProcessId());
			int arg = Machine.readRegister(4);
			if(JNachos.processWaitingTable.containsKey(finishingProcess.getProcessId())){
				NachosProcess waitingProcess = JNachos.processWaitingTable.get(finishingProcess.getProcessId());
				System.out.println("Process PID: " + waitingProcess.getProcessId()+" was waiting for PID "+ finishingProcess.getProcessId());
				System.out.println("Writing exit argument " + arg + " to register 2. Return of Join is : " +arg);
				Machine.writeRegister(2,arg);
				JNachos.processWaitingTable.remove(finishingProcess.getProcessId());
				System.out.println("Process added to ready queue PID : " + waitingProcess.getProcessId());
				Scheduler.readyToRun(waitingProcess);
			}
			System.out
					.println("Current Process PID " + JNachos.getCurrentProcess().getProcessId()+ " " + JNachos.getCurrentProcess().getName() + " exiting with code " + arg);

			// Finish the invoking process
			JNachos.getCurrentProcess().finish();
			break;
			case SC_Fork :
				oldLevel = Interrupt.setLevel(false);
				NachosProcess childProcess = new NachosProcess("Child Process");
				NachosProcess parentProcess = JNachos.getCurrentProcess();
				AddrSpace addrSpace = new AddrSpace(parentProcess.getSpace());
				childProcess.setSpace(addrSpace);
				Machine.writeRegister(2,0);
				childProcess.saveUserState();
				childProcess.fork(new ForkedProcessHandler(),childProcess);
				Machine.writeRegister(2,childProcess.getProcessId());
				System.out.println("(SysCall : "+pWhichSysCall+") Fork system call called by the process PID : "+parentProcess.getProcessId());
				System.out.println("Child process PID is : " + childProcess.getProcessId());
				Interrupt.setLevel(oldLevel);
				break;

			case SC_Join:
				System.out.println("(SysCall : "+pWhichSysCall+") Join system call from PID " + JNachos.getCurrentProcess().getProcessId());
				oldLevel = Interrupt.setLevel(false);
				int requestedPid = Machine.readRegister(4);
				System.out.println("Join system call made to wait for PID : " + requestedPid );
				NachosProcess invokingProcess = JNachos.getCurrentProcess();
				if( requestedPid == invokingProcess.getProcessId() || !JNachos.processTable.containsKey(requestedPid)){
					System.out.println("Returning from  join system call! Call made to self or invalid process from PID "+invokingProcess.getProcessId());
					return;
				}
				JNachos.processWaitingTable.put(requestedPid,invokingProcess);
				invokingProcess.sleep();
				Interrupt.setLevel(oldLevel);
				break;

			case SC_Exec:
				String fileName = new String();
				oldLevel = Interrupt.setLevel(false);
				int startingAddress = Machine.readRegister(4);
				int startingValue = 1;
				while((char) startingValue != '\0'){
					startingValue = Machine.readMem(startingAddress,1);
					if((char) startingValue != '\0'){
						fileName += (char) startingValue;
					}
					startingAddress++;
				}
				String executableName = fileName;
				if(fileName.length()!=0) {
					System.out.println("Executing exec (SysCall : "+pWhichSysCall+")" + " call from PID " + JNachos.getCurrentProcess().getProcessId() +" fileName : "+ fileName);
					SystemCallHandler.execCallHandler(executableName, JNachos.getCurrentProcess());
				}
				Interrupt.setLevel(oldLevel);
				break;

		default:
			Interrupt.halt();
			break;
		}
	}

	public static void execCallHandler(String executableFileName,NachosProcess currentProcess){
		OpenFile executableFile = JNachos.mFileSystem.open(executableFileName);
		if(executableFile == null){
			System.out.println("Unable to open file");
			return;
		}
		AddrSpace addrSpace = new AddrSpace(executableFile);
		currentProcess.setSpace(addrSpace);
		addrSpace.initRegisters();
		currentProcess.getSpace().restoreState();
		Machine.run();
	}

}
