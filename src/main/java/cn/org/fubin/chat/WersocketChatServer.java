package cn.org.fubin.chat;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedNioFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.ImmediateEventExecutor;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by fubin on 2019/7/14.
 */
public class WersocketChatServer {

    //创建DefaultChannelGroup，其将保存所有已经连接的WebSocket Channel
    private static final ChannelGroup channelGroup = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    private static final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private static final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private Channel channel;

    public static void main(String[] args) {
        try{
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup,workerGroup)
                    .channel(NioServerSocketChannel.class)
                    //增加日志handler
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChatWebsocketChatInitializer(channelGroup));

            ChannelFuture channelFuture = serverBootstrap.bind(new InetSocketAddress(8899)).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

class ChatWebsocketChatInitializer extends ChannelInitializer<Channel> {

    private final ChannelGroup group;

    public ChatWebsocketChatInitializer(ChannelGroup group) {
        this.group = group;
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new HttpServerCodec());
        pipeline.addLast(new ChunkedWriteHandler());
        pipeline.addLast(new HttpObjectAggregator(64*1024));
        //处理http请求
        pipeline.addLast(new HttpRequestHandler("/ws"));
        //处理websocket请求
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
        pipeline.addLast(new ChatTextWebsocketFrameHandler(group));
    }
}

/**
 *
 *
 * 管理http的请求和响应
 */

class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
    private final String wsUri;
    private static final File INDEX;

    static {
        URL location = HttpRequestHandler.class.getProtectionDomain().getCodeSource().getLocation();

        try {
            String path = location.toURI() + "index.html";
            path = !path.contains("file:")?path:path.substring(5);
            INDEX = new File(path);
        } catch (URISyntaxException e) {
            throw  new IllegalStateException("Unable to locate index.html",e);
        }
    }

    public HttpRequestHandler(String wsUri) {
        this.wsUri = wsUri;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        if(wsUri.equalsIgnoreCase(msg.getUri())){
            //如果请求了websocket协议升级，则增加引用计数，调用retain方法，并将它传给下一个ChannelInboundHandler
            ctx.fireChannelRead(msg.retain());
        }else{
            //处理100 continue请求以符合http1.1规范
            if(HttpHeaders.is100ContinueExpected(msg)){
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.CONTINUE);
                ctx.writeAndFlush(response);
            }
            //读取index.html
            RandomAccessFile file = new RandomAccessFile(INDEX,"r");
            HttpResponse response = new DefaultHttpResponse(msg.getProtocolVersion(),HttpResponseStatus.OK);
            response.headers().set(HttpHeaders.Names.CONTENT_TYPE,"text/plain;charset=UTF-8");
            //如果请求了keep-alive，则添加所需要的http头信息
            boolean keepAlive = HttpHeaders.isKeepAlive(msg);
            if(keepAlive){
                response.headers().set(HttpHeaders.Names.CONTENT_LENGTH,file.length());
                response.headers().set(HttpHeaders.Names.CONNECTION,HttpHeaders.Values.KEEP_ALIVE);
            }
            //将httpresponse写到客户端
            ctx.write(response);
            //将index.html写到客户端
            if(ctx.pipeline().get(SslHandler.class) == null){
                ctx.write(new DefaultFileRegion(file.getChannel(),0,file.length()));
            }else{
                ctx.write(new ChunkedNioFile(file.getChannel()));
            }
            //写LastHttpContent冲刷到客户端
            ChannelFuture future = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if(!keepAlive){
                // 如果没有请求keep-alive，则写操作完成后关闭Channel
                future.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}

class ChatTextWebsocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame>{

    private final ChannelGroup group;

    public ChatTextWebsocketFrameHandler(ChannelGroup group) {
        this.group = group;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        //增加消息的引用计数，并将它写到ChannelGroup中所有已经连接的客户端
        group.writeAndFlush(msg.retain());
    }

    /**
     * 重写userEventTriggered方法处理自定义事件
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE){
            //如果该事件表示我手成功，则从该Channelpipeline中移除HttpRequestHandler，因为将不会收到任何http消息了
            ctx.pipeline().remove(HttpRequestHandler.class);
            //通知所有已经连接的websocket客户端新的客户端已经连接上了
            group.writeAndFlush(new TextWebSocketFrame("client : "+ ctx.channel() + " joined "));
            //将心的Websocket Channel添加到ChannelGroup中，以便它可以接收到所有消息
            group.add(ctx.channel());
        }else {
            super.userEventTriggered(ctx, evt);
        }

    }
}
