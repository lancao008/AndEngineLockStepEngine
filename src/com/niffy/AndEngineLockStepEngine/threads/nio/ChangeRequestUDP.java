package com.niffy.AndEngineLockStepEngine.threads.nio;

import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

public class ChangeRequestUDP extends ChangeRequest {
	// ===========================================================
	// Constants
	// ===========================================================
	// ===========================================================
	// Fields
	// ===========================================================
	// ===========================================================
	// Constructors
	// ===========================================================

	public ChangeRequestUDP(DatagramChannel pChannel, int pType, int pOps, InetSocketAddress pAddress) {
		super(pChannel, pType, pOps, pAddress);
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}
