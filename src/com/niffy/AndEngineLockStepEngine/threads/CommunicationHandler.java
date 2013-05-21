package com.niffy.AndEngineLockStepEngine.threads;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.os.Looper;
import android.os.Message;

import com.niffy.AndEngineLockStepEngine.flags.ErrorCodes;
import com.niffy.AndEngineLockStepEngine.flags.ITCFlags;
import com.niffy.AndEngineLockStepEngine.messages.IMessage;
import com.niffy.AndEngineLockStepEngine.misc.IHandlerMessage;
import com.niffy.AndEngineLockStepEngine.misc.WeakThreadHandler;
import com.niffy.AndEngineLockStepEngine.options.IBaseOptions;
import com.niffy.AndEngineLockStepEngine.threads.nio.IClientSelector;
import com.niffy.AndEngineLockStepEngine.threads.nio.IServerSelector;
import com.niffy.AndEngineLockStepEngine.threads.nio.SelectorFlag;

public class CommunicationHandler extends CommunicationThread implements ICommunicationHandler {
	// ===========================================================
	// Constants
	// ===========================================================
	private final Logger log = LoggerFactory.getLogger(CommunicationHandler.class);
	// ===========================================================
	// Fields
	// ===========================================================
	protected IClientSelector mUDP;
	protected IServerSelector mTCPServer;
	protected IClientSelector mTCPClient;
	protected IClientSelector mMainSelector;
	protected int mCurrentSelectorInUse;

	// ===========================================================
	// Constructors
	// ===========================================================

	public CommunicationHandler(final String pName, final InetSocketAddress pAddress,
			WeakThreadHandler<IHandlerMessage> pCaller, final IBaseOptions pOptions) {
		super(pName, pAddress, pCaller, pOptions);
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void setUDPSelectorThread(IClientSelector pSelectorThread) {
		this.mUDP = pSelectorThread;
	}

	@Override
	public void setTCPClientSelectorThread(IClientSelector pSelectorThread) {
		this.mTCPClient = pSelectorThread;
	}

	@Override
	public void setTCPServerSelectorThread(IServerSelector pSelectorThread) {
		this.mTCPServer = pSelectorThread;
	}

	@Override
	public void run() {
		Looper.prepare();
		this.mRunning.set(true);
		this.mHandler = new WeakThreadHandler<IHandlerMessage>(this, Looper.myLooper());
		Thread udp = (Thread) this.mUDP;
		udp.start();
		Thread tcpClient = (Thread) this.mTCPClient;
		tcpClient.start();
		Thread tcpServer = (Thread) this.mTCPServer;
		tcpServer.start();
		Looper.loop();
	}

	@Override
	public void handlePassedMessage(Message pMessage) {
		/*
		 * TODO message to select either UDP or TCPClient to send messages
		 */
		log.debug("Handling message: {}", pMessage.what);
		super.handlePassedMessage(pMessage);
		Bundle bundle;
		String ip;
		byte[] data;
		switch (pMessage.what) {
		case ITCFlags.NETWORK_SELECTER_DEFAULT:
			bundle = pMessage.getData();
			int selector = bundle.getInt("selector", 2);
			this.setMainSelector(selector);
			break;
		case ITCFlags.NEW_CLIENT_CONNECTED:
			bundle = pMessage.getData();
			ip = bundle.getString("ip");
			this.clientJoin(ip);
			break;
		case ITCFlags.CLIENT_ERROR:
			bundle = pMessage.getData();
			ip = bundle.getString("ip");
			break;
		case ITCFlags.CLIENT_DISCONNECTED:
			bundle = pMessage.getData();
			ip = bundle.getString("ip");
			this.clientDisconnect(ip);
			break;
		}
	}

	@Override
	protected void connect(final String pAddress) {
		/*
		 * TODO send to TCPClient to create connection.
		 * TODO implement a circle. When we connect as client we expect a connection back in the server region
		 */
	}

	@Override
	public <T extends IMessage> int sendMessage(InetAddress pAddress, T pMessage) {
		log.debug("Send Message to: {}", pAddress.getHostAddress());
		byte[] pData = null;
		while (!Thread.interrupted() && this.mRunning.get() && !this.mTerminated.get()) {
			try {
				String addr = pAddress.getHostAddress();
				final ByteArrayOutputStream bOutput = new ByteArrayOutputStream();
				final DataOutputStream dOutput = new DataOutputStream(bOutput);
				pMessage.write(dOutput);
				dOutput.flush();
				bOutput.flush();
				pData = bOutput.toByteArray();
				// TODO send via mMainSelector
			} catch (IOException e) {
				log.error("Error sending message to client: {}", pAddress, e);
				this.networkMessageFailure(pAddress.toString(), pData, ITCFlags.NETWORK_SEND_MESSAGE_FAILURE,
						ErrorCodes.COULD_NOT_SEND);
			}
		}
		return 0;
	}

	@Override
	protected void producePoolItems() {
		super.producePoolItems();
	}

	@Override
	protected void sendMessageWithPacketHandler(final int pIntended, InetAddress pAddress, byte[] pData) {
		super.sendMessageWithPacketHandler(pIntended, pAddress, pData);
	}

	@Override
	protected void sendMessageWithPacketHandler(final int pIntended, byte[] pData) {
		super.sendMessageWithPacketHandler(pIntended, pData);
	}

	@Override
	public void terminate() {
		log.warn("Terminating the thread");
		if (!this.mTerminated.getAndSet(true)) {
			this.mRunning.getAndSet(false);
			this.interrupt();
		}
	}

	@Override
	public void removeClient(InetAddress pAddress) {
		super.removeClient(pAddress);
		this.mTCPClient.removeClient(pAddress);
		this.mTCPServer.removeClient(pAddress);
		this.mUDP.removeClient(pAddress);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================
	protected void clientJoin(final String pIP) {
		InetAddress pAddress = this.castStringToAddress(pIP);
		if (pAddress != null) {
			this.clientJoin(pAddress);
		} else {
			log.error("Could not cast IP to InetAddress on client join");
			/*
			 * TODO cannot cast address so remove clients
			 */
		}
	}

	/**
	 * Handle the logic of all selectors aware of client and have a valid
	 * connection, if so inform the game of new client.
	 * 
	 * @param pAddress
	 */
	protected void clientJoin(final InetAddress pAddress) {
		boolean clientContains = false;
		boolean serverContains = false;
		if (this.mTCPClient.containsClient(pAddress)) {
			clientContains = true;
		} else {
			/*
			 * TODO get TCPServer port from options, call connect to
			 */
		}

		if (this.mTCPServer.containsClient(pAddress)) {
			serverContains = true;
		} else {
			// Not got a connection yet!
		}

		if (clientContains && serverContains) {
			this.mPacketHandler.addClient(pAddress);
			Message msg = this.mCallerThreadHandler.obtainMessage();
			msg.what = ITCFlags.CLIENT_CONNECTED;
			final Bundle pBundle = new Bundle();
			pBundle.putString("ip", pAddress.getHostAddress());
			msg.setData(pBundle);
			this.mCallerThreadHandler.sendMessage(msg);
		}
	}

	protected void clientDisconnect(final String pIP) {
		InetAddress pAddress = this.castStringToAddress(pIP);
		if (pAddress != null) {
			this.clientDisconnect(pAddress);
		} else {
			log.error("Could not cast IP to InetAddress on client disconnect");
			/*
			 * TODO throw error
			 */
		}
	}

	protected void clientDisconnect(final InetAddress pAddress) {
		this.removeClient(pAddress);
		Message msg = this.mCallerThreadHandler.obtainMessage();
		msg.what = ITCFlags.CLIENT_DISCONNECTED;
		final Bundle pBundle = new Bundle();
		pBundle.putString("ip", pAddress.getHostAddress());
		msg.setData(pBundle);
		this.mCallerThreadHandler.sendMessage(msg);
	}

	protected void clientError(final String pIP) {
		InetAddress pAddress = this.castStringToAddress(pIP);
		if (pAddress != null) {
			this.clientError(pAddress);
		} else {
			log.error("Could not cast IP to InetAddress on client error");
			/*
			 * TODO throw error?
			 */
		}
	}

	protected void clientError(final InetAddress pAddress) {
		this.removeClient(pAddress);
		Message msg = this.mCallerThreadHandler.obtainMessage();
		msg.what = ITCFlags.CLIENT_ERROR;
		final Bundle pBundle = new Bundle();
		pBundle.putString("ip", pAddress.getHostAddress());
		msg.setData(pBundle);
		this.mCallerThreadHandler.sendMessage(msg);
	}

	protected void setMainSelector(final int pSelection) {
		this.mCurrentSelectorInUse = pSelection;
		if (pSelection == SelectorFlag.TCP_CLIENT) {
			this.mMainSelector = this.mTCPClient;
		} else if (pSelection == SelectorFlag.UDP) {
			this.mMainSelector = this.mUDP;
		} else {
			this.mMainSelector = this.mTCPClient;
		}

	}
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}