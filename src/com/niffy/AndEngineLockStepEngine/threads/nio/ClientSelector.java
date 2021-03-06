package com.niffy.AndEngineLockStepEngine.threads.nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.os.Message;

import com.niffy.AndEngineLockStepEngine.exceptions.ClientDoesNotExist;
import com.niffy.AndEngineLockStepEngine.exceptions.ClientPendingClosure;
import com.niffy.AndEngineLockStepEngine.exceptions.NotConnectedToClient;
import com.niffy.AndEngineLockStepEngine.flags.ITCFlags;
import com.niffy.AndEngineLockStepEngine.misc.IHandlerMessage;
import com.niffy.AndEngineLockStepEngine.misc.WeakThreadHandler;
import com.niffy.AndEngineLockStepEngine.options.IBaseOptions;

public class ClientSelector extends BaseSelectorThread implements IClientSelector {
	// ===========================================================
	// Constants
	// ===========================================================
	private final Logger log = LoggerFactory.getLogger(ClientSelector.class);

	// ===========================================================
	// Fields
	// ===========================================================

	// ===========================================================
	// Constructors
	// ===========================================================
	/**
	 * @see {@link BaseSelectorThread#BaseSelectorThread(String, InetSocketAddress, WeakThreadHandler, IBaseOptions, int)}
	 */
	public ClientSelector(final String pName, final InetSocketAddress pAddress,
			WeakThreadHandler<IHandlerMessage> pCaller, final IBaseOptions pOptions) throws IOException {
		super(pName, pAddress, pCaller, pOptions);
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces BaseSelectorThread
	// ===========================================================
	@Override
	public void run() {
		log.debug("Running TCP Client Selector Thread");
		this.mRunning.set(true);
		Message msg = this.mCallerThreadHandler.obtainMessage();
		msg.what = ITCFlags.TCP_CLIENT_SELECTOR_START;
		this.mCallerThreadHandler.sendMessage(msg);
		while (true) {
			try {
				// Process any pending changes
				synchronized (this.mPendingChanges) {
					Iterator<ChangeRequest> changes = this.mPendingChanges.iterator();
					while (changes.hasNext()) {
						ChangeRequest change = (ChangeRequest) changes.next();
						this.handleChangeRequest(change);
					}
					this.mPendingChanges.clear();
				}

				// Wait for an event one of the registered channels
				this.mSelector.select();

				// Iterate over the set of keys for which events are available
				Iterator<SelectionKey> selectedKeys = this.mSelector.selectedKeys().iterator();
				while (selectedKeys.hasNext()) {
					SelectionKey key = (SelectionKey) selectedKeys.next();
					selectedKeys.remove();

					if (!key.isValid()) {
						continue;
					}

					// Check what event is available and deal with it
					try {
						if (key.isConnectable()) {
							this.finishConnection(key);
						} else if (key.isReadable()) {
							this.read(key);
						} else if (key.isWritable()) {
							this.write(key);
						}
					} catch (IOException e) {
						log.error("IOException on key operation", e);
					}
				}
			} catch (Exception e) {
				log.error("Exception in main loop", e);
			}
		}
	}

	@Override
	protected void finishConnection(SelectionKey pKey) throws IOException {
		log.debug("finishConnection");
		SocketChannel socketChannel;
		InetSocketAddress address;
		Connection con = (Connection) pKey.attachment();
		if (con != null) {
			socketChannel = con.getSocketChannel();
			address = con.getAddress();
		} else {
			socketChannel = (SocketChannel) pKey.channel();
			address = (InetSocketAddress) socketChannel.socket().getRemoteSocketAddress();
			con = new Connection(address, socketChannel);
			pKey.attach(con);
		}

		try {
			socketChannel.finishConnect();
		} catch (NoConnectionPendingException e) {
			log.error("NoConnectionPendingException", e);
			this.handleConnectionFailure(pKey, socketChannel, address.getAddress());
		} catch (ClosedChannelException e) {
			log.error("ClosedChannelException", e);
			this.handleConnectionFailure(pKey, socketChannel, address.getAddress());
		} catch (IOException e) {
			log.error("IOException", e);
			this.handleConnectionFailure(pKey, socketChannel, address.getAddress());
			return;
		}
		synchronized (this.mChannelMap) {
			this.mChannelMap.put(con.mAddress.getAddress(), con);
		}
		pKey.interestOps(SelectionKey.OP_WRITE);

		Message msg = this.mCallerThreadHandler.obtainMessage();
		msg.what = ITCFlags.NEW_CLIENT_CONNECTED;
		Bundle data = new Bundle();
		data.putString("ip", address.getAddress().getHostAddress());
		msg.setData(data);
		this.mCallerThreadHandler.sendMessage(msg);
	}

	/**
	 * @throws IOException
	 *             due to {@link SocketChannel#write(ByteBuffer)} call
	 * @throws CancelledKeyException
	 * @see com.niffy.AndEngineLockStepEngine.threads.nio.BaseSelectorThread#write(java.nio.channels.SelectionKey)
	 */
	@Override
	protected void write(SelectionKey pKey) throws IOException, CancelledKeyException {
		SocketChannel socketChannel;
		String connectionIP;
		Connection con = (Connection) pKey.attachment();
		if (con != null) {
			socketChannel = con.getSocketChannel();
			connectionIP = con.getAddress().getAddress().getHostAddress();
		} else {
			socketChannel = (SocketChannel) pKey.channel();
			InetSocketAddress address = (InetSocketAddress) socketChannel.socket().getRemoteSocketAddress();
			connectionIP = address.getAddress().getHostAddress();
			log.warn("Could not get Connection attachment for IP: {}", connectionIP);
		}

		synchronized (this.mPendingData) {
			ArrayList<ByteBuffer> queue = this.mPendingData.get(con.getAddress().getAddress());

			// Write until there's not more data ...
			while (!queue.isEmpty()) {
				ByteBuffer buf = (ByteBuffer) queue.get(0);
				socketChannel.write(buf);
				if (buf.remaining() > 0) {
					// ... or the socket's buffer fills up
					break;
				}
				queue.remove(0);
			}

			if (queue.isEmpty()) {
				// We wrote away all data, so we're no longer interested
				// in writing on this socket. Switch back to waiting for
				// data.
				pKey.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	@Override
	protected void handleChangeRequest(ChangeRequest pChangeRequest) {
		log.debug("Change Request: {}", pChangeRequest.mType);
		switch (pChangeRequest.mType) {
		case ChangeRequest.CHANGEOPS:
			SelectionKey key = pChangeRequest.mChannel.keyFor(this.mSelector);
			if (key == null) {
				log.error("Could not change channel operations for. Null key {} ", pChangeRequest.mChannel.toString());
			} else {
				try {
					key.interestOps(pChangeRequest.mOps);
				} catch (IllegalArgumentException e) {
					log.error("IllegalArgumentException", e);
					/* TODO handle this, clean up pending data and pending changes?
					 * And remove from any collections
					 */
				} catch (CancelledKeyException e) {
					log.error("CancelledKeyException", e);
					/* TODO handle this, clean up pending data and pending changes?
					 * And remove from any collections
					 */
				}
			}
			break;
		case ChangeRequest.REGISTER:
			try {
				SelectionKey keyFound = pChangeRequest.mChannel.register(this.mSelector, pChangeRequest.mOps);
				/*
				 * fixes #1
				 * Hack to finish connection. When making the connection the socket 
				 * channel doesn't seem to have the IP address we're connecting to,
				 * which is use to store a connection reference.
				 * So we create one early on!
				 */
				Connection con = null;
				if (pChangeRequest.mSocketAddress != null) {
					con = new Connection(pChangeRequest.mSocketAddress, pChangeRequest.getAsSocketChannel());
				} else {
					InetSocketAddress address = new InetSocketAddress(pChangeRequest.mAddress,
							this.mBaseOptions.getTCPServerPort());
					con = new Connection(address, pChangeRequest.getAsSocketChannel());
				}
				keyFound.attach(con);
				this.createQueue(con);
			} catch (ClosedChannelException e) {
				log.error("ClosedChannelException", e);
				/* TODO handle this, clean up pending data and pending changes?
				 * And remove from any collections
				 */
			} catch (CancelledKeyException e) {
				log.error("CancelledKeyException", e);
				/* TODO handle this, clean up pending data and pending changes?
				 * And remove from any collections
				 */
			}
			break;
		case ChangeRequest.REMOVECLIENT:
			try {
				this.handleConnectionShutdown(pChangeRequest.mChannel.keyFor(this.mSelector), pChangeRequest.mChannel,
						pChangeRequest.mAddress);
			} catch (IOException e) {
				log.error("Could not shut downconnection.", e);
			} catch (ClientDoesNotExist e) {
				log.error("Could not shut downconnection.", e);
			}
			break;
		}
	}

	// ===========================================================
	// Methods for/from SuperClass/Interfaces IClientSelector
	// ===========================================================

	@Override
	public void send(InetAddress pAddress, byte[] pData) throws NotConnectedToClient, ClientDoesNotExist,
			ClientPendingClosure {
		synchronized (this.mPendingClosure) {
			if (this.mPendingClosure.contains(pAddress)) {
				final String pMessage = "Address: " + pAddress.toString() + "  is pending closure.";
				throw new ClientPendingClosure(pMessage);
			}
		}
		synchronized (this.mChannelMap) {
			if (this.mChannelMap.containsKey(pAddress)) {
				Connection con = this.mChannelMap.get(pAddress);
				if (!con.mSocketChannel.isConnected()) {
					log.error("Went to send a message to: {} but the channel is not connected", pAddress.toString());
					final String pMessage = "Address: " + pAddress.toString() + " Is added but not is not connected.";
					throw new NotConnectedToClient(pMessage);
				} else {
					/* TODO send message */
				}
			} else {
				log.error("Went to send a message to: {} but no channel exists", pAddress.toString());
				final String pMessage = "Address: " + pAddress.toString() + "  but no channel exists.";
				throw new ClientDoesNotExist(pMessage);
			}
		}
	}

	@Override
	public void connectTo(InetSocketAddress pAddress) throws IOException {
		this.initiateConnection(pAddress);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods
	// ===========================================================
	protected SocketChannel initiateConnection(final InetSocketAddress pAddress) throws IOException {
		log.debug("InitiateConnection: {}", pAddress);
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);

		// Kick off connection establishment
		socketChannel.connect(pAddress);

		// Queue a channel registration since the caller is not the
		// selecting thread. As part of the registration we'll register
		// an interest in connection events. These are raised when a channel
		// is ready to complete connection establishment.
		synchronized (this.mPendingChanges) {
			this.mPendingChanges.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT,
					pAddress.getAddress(), pAddress));
		}
		this.mSelector.wakeup();
		return socketChannel;
	}

	protected void sendMessage(final Connection pConnection, final byte[] pData) {
		synchronized (this.mPendingData) {
			ArrayList<ByteBuffer> queue = this.mPendingData.get(pConnection.getAddress());
			if (queue == null) {
				queue = new ArrayList<ByteBuffer>();
				this.mPendingData.put(pConnection.getAddress().getAddress(), queue);
			}
			queue.add(ByteBuffer.wrap(pData));
		}

		this.mSelector.wakeup();
	}
	
	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

}
