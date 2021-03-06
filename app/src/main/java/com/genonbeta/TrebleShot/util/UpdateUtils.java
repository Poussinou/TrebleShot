package com.genonbeta.TrebleShot.util;

import android.content.Context;
import android.content.pm.PackageManager;

import com.genonbeta.TrebleShot.config.AppConfig;
import com.genonbeta.TrebleShot.io.DocumentFile;
import com.genonbeta.TrebleShot.object.NetworkDevice;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * created by: Veli
 * date: 30.12.2017 17:08
 */

public class UpdateUtils
{
	public static void sendUpdate(Context context, String toIp) throws IOException
	{
		Socket socket = new Socket();

		socket.bind(null);
		socket.connect(new InetSocketAddress(toIp, AppConfig.SERVER_PORT_UPDATE_CHANNEL));

		FileInputStream fileInputStream = new FileInputStream(context.getApplicationInfo().sourceDir);
		OutputStream outputStream = socket.getOutputStream();

		byte[] buffer = new byte[AppConfig.BUFFER_LENGTH_DEFAULT];
		int len;
		long lastRead = System.currentTimeMillis();

		while ((len = fileInputStream.read(buffer)) != -1) {
			if (len > 0) {
				outputStream.write(buffer, 0, len);
				outputStream.flush();

				lastRead = System.currentTimeMillis();
			}

			if ((System.currentTimeMillis() - lastRead) > AppConfig.DEFAULT_SOCKET_TIMEOUT) {
				System.out.println("CoolTransfer: Timed out... Exiting.");
				break;
			}
		}

		outputStream.close();
		fileInputStream.close();

		socket.close();
	}

	public static DocumentFile receiveUpdate(Context context, NetworkDevice device, Interrupter interrupter, OnConnectionReadyListener readyListener) throws IOException, PackageManager.NameNotFoundException
	{
		ServerSocket serverSocket = null;
		Socket socket = null;
		DocumentFile updateFile = null;

		try {
			serverSocket = new ServerSocket(AppConfig.SERVER_PORT_UPDATE_CHANNEL);
			updateFile = FileUtils.getApplicationDirectory(context)
					.createFile(null, device.versionName + "_" + System.currentTimeMillis() + ".apk");

			final ServerSocket finalServer = serverSocket;

			interrupter.addCloser(new Interrupter.Closer()
			{
				@Override
				public void onClose(boolean userAction)
				{
					try {
						if (!finalServer.isClosed())
							finalServer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});

			if (readyListener != null)
				readyListener.onConnectionReady(serverSocket);

			serverSocket.setSoTimeout(AppConfig.DEFAULT_SOCKET_TIMEOUT);

			socket = serverSocket.accept();

			socket.setSoTimeout(AppConfig.DEFAULT_SOCKET_TIMEOUT);

			InputStream inputStream = socket.getInputStream();
			OutputStream outputStream = context.getContentResolver().openOutputStream(updateFile.getUri());

			byte[] buffer = new byte[AppConfig.BUFFER_LENGTH_DEFAULT];
			int len = 0;
			long lastRead = System.currentTimeMillis();

			while (len != -1) {
				if ((len = inputStream.read(buffer)) > 0) {
					outputStream.write(buffer, 0, len);
					outputStream.flush();

					lastRead = System.currentTimeMillis();
				}

				if ((System.currentTimeMillis() - lastRead) > AppConfig.DEFAULT_SOCKET_TIMEOUT || interrupter.interrupted())
					throw new Exception("Timed out or interrupted. Exiting!");
			}

			outputStream.close();
			inputStream.close();

		} catch (Exception e) {
			e.printStackTrace();

			if (updateFile != null && updateFile.isFile())
				updateFile.delete();

			return null;
		} finally {
			if (socket != null && !socket.isClosed())
				socket.close();

			if (serverSocket != null && !serverSocket.isClosed())
				serverSocket.close();
		}

		return updateFile;
	}

	public interface OnConnectionReadyListener
	{
		void onConnectionReady(ServerSocket socket);
	}
}
