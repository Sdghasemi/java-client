/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package microsoft.aspnet.signalr.client.transport;

import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import microsoft.aspnet.signalr.client.ConnectionBase;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.Platform;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.UpdateableCancellableFuture;
import microsoft.aspnet.signalr.client.http.HttpConnection;

/**
 * Implements the WebsocketTransport for the Java SignalR library
 * Created by stas on 07/07/14.
 */
public class WebsocketTransport extends HttpClientTransport {
    private static final String USER_AGENT_HEADER = "User-Agent";

    private String mPrefix;
    private static final Gson gson = new Gson();
    WebSocketClient mWebSocketClient;
    private UpdateableCancellableFuture<Void> mConnectionFuture;

    public WebsocketTransport(Logger logger) {
        super(logger);
    }

    public WebsocketTransport(Logger logger, HttpConnection httpConnection) {
        super(logger, httpConnection);
    }

    @Override
    public String getName() {
        return "webSockets";
    }

    @Override
    public boolean supportKeepAlive() {
        return true;
    }

    @Override
    public SignalRFuture<Void> start(final ConnectionBase connection, ConnectionType connectionType, final DataResultCallback callback) {
        final String connectionString = connectionType == ConnectionType.InitialConnection ? "connect" : "reconnect";

        final String transport = getName();
        final String connectionToken = connection.getConnectionToken();
        final String messageId = connection.getMessageId() != null ? connection.getMessageId() : "";
        final String groupsToken = connection.getGroupsToken() != null ? connection.getGroupsToken() : "";
        final String connectionData = connection.getConnectionData() != null ? connection.getConnectionData() : "";

        boolean isSsl = false;
        String url = null;
        try {
            url = connection.getUrl() + connectionString + '?'
                    + "connectionData=" + URLEncoder.encode(URLEncoder.encode(connectionData, "UTF-8"), "UTF-8")
                    + "&connectionToken=" + URLEncoder.encode(URLEncoder.encode(connectionToken, "UTF-8"), "UTF-8")
                    + "&groupsToken=" + URLEncoder.encode(groupsToken, "UTF-8")
                    + "&messageId=" + URLEncoder.encode(messageId, "UTF-8")
                    + "&transport=" + URLEncoder.encode(transport, "UTF-8");
            if (connection.getQueryString() != null) {
                url += "&" + connection.getQueryString();
            }
            if (url.startsWith("https://")) {
                isSsl = true;
                url = url.replace("https://", "wss://");
            }
            else if (url.startsWith("http://")) {
                url = url.replace("http://", "ws://");
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        mConnectionFuture = new UpdateableCancellableFuture<Void>(null);

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            mConnectionFuture.triggerError(e);
            return mConnectionFuture;
        }
        Map<String, String> headers = connection.getHeaders();
        headers.put(USER_AGENT_HEADER, Platform.getUserAgent());
        log("uri:" +uri+","+connection.getHeaders(), LogLevel.Verbose);
        mWebSocketClient = new WebSocketClient(uri, new Draft_17(), headers, 0) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                log("onOpen:" , LogLevel.Verbose);
                mConnectionFuture.setResult(null);
            }
            @Override
            public void onMessage(String s) {
                log("onMessage:"+s, LogLevel.Verbose);
                callback.onData(s);
            }
            @Override
            public void onClose(int i, String s, boolean b) {
                mWebSocketClient.close();
                connection.onError(new RuntimeException(s),true);
                log("onClose:"+s, LogLevel.Critical);
            }
            @Override
            public void onError(Exception e) {
                mConnectionFuture.triggerError(e);
                log("onError:", LogLevel.Critical);
            }
        };

        if (isSsl) {
            try {
                SSLContext sslContext = null;
                sslContext = SSLContext.getInstance( "TLS" );
                sslContext.init( null, null, null ); // will use java's default key and trust store which is sufficient unless you deal with self-signed certificates
                SSLSocketFactory factory = sslContext.getSocketFactory();// (SSLSocketFactory) SSLSocketFactory.getDefault();
                mWebSocketClient.setSocket(factory.createSocket());
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }

        mWebSocketClient.connect();
        connection.closed(new Runnable() {
            @Override
            public void run() {
                mWebSocketClient.close();
            }
        });
        return mConnectionFuture;
    }

    @Override
    public SignalRFuture<Void> send(ConnectionBase connection, String data, DataResultCallback callback) {
        mWebSocketClient.send(data);
        return new UpdateableCancellableFuture<Void>(null);
    }
}