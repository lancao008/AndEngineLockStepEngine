package com.niffy.AndEngineLockStepEngine.threads;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Looper;
import android.os.Message;

import com.niffy.AndEngineLockStepEngine.messages.MessageError;
import com.niffy.AndEngineLockStepEngine.misc.IHandlerMessage;
import com.niffy.AndEngineLockStepEngine.misc.WeakThreadHandler;
import com.niffy.AndEngineLockStepEngine.options.IBaseOptions;

public abstract class BaseCommunicationThread extends Thread implements IBaseCommunicationThread {
	// ===========================================================
	// Constants
	// ===========================================================
	private final Logger log = LoggerFactory.getLogger(BaseCommunicationThread.class);

	// ===========================================================
	// Fields
	// ===========================================================
	protected InetAddress mAddress;
	protected IBaseOptions mBaseOptions;
	protected final AtomicBoolean mRunning = new AtomicBoolean(false);
	protected final AtomicBoolean mTerminated = new AtomicBoolean(false);
	protected final AtomicBoolean mIgnoreIncoming = new AtomicBoolean(true);
	protected WeakThreadHandler<IHandlerMessage> mCallerThreadHandler;
	protected WeakThreadHandler<IHandlerMessage> mHandler;

	// ===========================================================
	// Constructors
	// ===========================================================

	public BaseCommunicationThread(final String pName, final InetAddress pAddress,
			WeakThreadHandler<IHandlerMessage> pCaller, final IBaseOptions pOptions) {
		super(pName);
		this.mAddress = pAddress;
		this.mCallerThreadHandler = pCaller;
		this.mBaseOptions = pOptions;
	}
	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================
	
	@Override
	public void handlePassedMessage(Message pMessage) {
	}

	@Override
	public WeakThreadHandler<IHandlerMessage> getParentHandler() {
		return this.mCallerThreadHandler;
	}

	@Override
	public WeakThreadHandler<IHandlerMessage> getHandler() {
		return this.mHandler;
	}

	@Override
	public boolean isRunning() {
		return this.mRunning.get();
	}

	@Override
	public boolean isTerminated() {
		return this.mTerminated.get();
	}

	@Override
	public void terminate() {
		log.warn("Terminating the thread: {}", this.getName());
		if (!this.mTerminated.getAndSet(true)) {
			this.mRunning.getAndSet(false);
			Looper.myLooper().quit();
			this.interrupt();
		}
	}

	@Override
	public boolean isIgnoring() {
		return this.mIgnoreIncoming.get();
	}


	@Override
	public void setIgnoreIncoming(boolean pAllow) {
		log.debug("SetIgnore: {}", pAllow);
		this.mIgnoreIncoming.getAndSet(pAllow);
	}

	@Override
	public void handleErrorMessage(InetAddress pAddress, MessageError pMessage) {
	}

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
