package com.niffy.AndEngineLockStepEngine.threads.nio;

import java.net.InetAddress;
import java.nio.channels.spi.AbstractSelectableChannel;

public class ChangeRequest {
	// ===========================================================
	// Constants
	// ===========================================================
	public static final int REGISTER = 1;
	public static final int CHANGEOPS = 2;
	public static final int REMOVECLIENT = 3;
	// ===========================================================
	// Fields
	// ===========================================================
	public AbstractSelectableChannel mChannel;
	public int mType;
	public int mOps;
	public InetAddress mAddress;

	// ===========================================================
	// Constructors
	// ===========================================================

	public ChangeRequest(AbstractSelectableChannel pSocketChannel, int pType, int pOps, InetAddress pAddress) {
		this.mChannel = pSocketChannel;
		this.mType = pType;
		this.mOps = pOps;
		this.mAddress = pAddress;
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
