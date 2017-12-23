/**
 * Copyright (c) 1992-1993 The Regents of the University of California.
 * All rights reserved.  See copyright.h for copyright notice and limitation 
 * of liability and disclaimer of warranty provisions.
 *
 *  Created by Patrick McSweeney on 12/13/08.
 *
 */
package jnachos.kern;

import jnachos.machine.*;

/**
 * The ExceptionHanlder class handles all exceptions raised by the simulated
 * machine. This class is abstract and should not be instantiated.
 */
public abstract class ExceptionHandler {

	/**
	 * This class does all of the work for handling exceptions raised by the
	 * simulated machine. This is the only funciton in this class.
	 *
	 * @param pException
	 *            The type of exception that was raised.
	 *
	 */
	public static void handleException(ExceptionType pException) {
		switch (pException) {
		// If this type was a system call
		case SyscallException:

			// Get what type of system call was made
			int type = Machine.readRegister(2);

			// Invoke the System call handler
			SystemCallHandler.handleSystemCall(type);
			break;
			case PageFaultException:
				int pageNumber = AddrSpace.mFreeMap.find();
				handlePageFault(pageNumber,JNachos.getCurrentProcess());
				break;
			// All other exceptions shut down for now
			default:
				System.exit(0);
		}
	}

	private static void handlePageFault(int pageNumber,NachosProcess currentProcess){

		TranslationEntry[] currentPageTable = currentProcess.getSpace().mPageTable;
		if(pageNumber < 0){

			TranslationEntry pageToBeEvicted = JNachos.pageList.removeFirst();
			if (pageToBeEvicted.dirty){
				pageToBeEvicted.dirty = false;
				byte[] evictedAddress = new byte[Machine.PageSize];
				System.arraycopy(Machine.mMainMemory,pageToBeEvicted.physicalPage*Machine.PageSize,evictedAddress,0,Machine.PageSize);
				JNachos.swapSpace.writeAt(evictedAddress,Machine.PageSize,pageToBeEvicted.pageInSwapSpace*Machine.PageSize);
			}
			pageNumber = pageToBeEvicted.physicalPage;
			pageToBeEvicted.physicalPage = -1;
			pageToBeEvicted.use = false;
			pageToBeEvicted.valid = false;
		}
		Statistics.numPageFaults++;
		int faultyVirtualAddress = Machine.readRegister(Machine.BadVAddrReg);
		int faultyPageNumber = faultyVirtualAddress/Machine.PageSize;
		byte[] bytes = new byte[Machine.PageSize];
		System.out.println("Total page faults : " + Statistics.numPageFaults);
		System.out.println("Faulty page : " + faultyPageNumber);
		System.out.println("Faulty address : " + faultyVirtualAddress);
		JNachos.swapSpace.readAt(bytes,Machine.PageSize,currentPageTable[faultyPageNumber].pageInSwapSpace*Machine.PageSize);
		System.arraycopy(bytes,0,Machine.mMainMemory,pageNumber*Machine.PageSize,Machine.PageSize);
		currentPageTable[faultyPageNumber].valid = true;
		currentPageTable[faultyPageNumber].use = true;
		currentPageTable[faultyPageNumber].physicalPage = pageNumber;
		if(JNachos.pageList.isEmpty()){
			JNachos.pageList.addFirst(currentPageTable[faultyPageNumber]);
		}
		else {
			JNachos.pageList.addLast(currentPageTable[faultyPageNumber]);
		}
	}
}

