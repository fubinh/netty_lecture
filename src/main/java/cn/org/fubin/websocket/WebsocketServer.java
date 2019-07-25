package cn.org.fubin.websocket;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;

/**
 * Created by fubin on 2019/7/14.
 */
public class WebsocketServer {
    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup,workerGroup)
                    .channel(NioServerSocketChannel.class)
                    //增加日志handler
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new WebsocketInitializer());

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

class WebsocketInitializer extends ChannelInitializer<SocketChannel>{
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        /**
         * 处理http请求
         */
        //http编解码
        pipeline.addLast("httpCodec",new HttpServerCodec());
        //以块的方式写的处理器
        pipeline.addLast(new ChunkedWriteHandler());
        //http消息聚合 -> FullHttpRequest或FullHttoResponse
        pipeline.addLast(new HttpObjectAggregator(8192));

        /**
         * 处理websocket
         */

        //websocket握手，控制帧Frames的处理
        // ws://server:port/content_path(hellowebsocket)
        pipeline.addLast(new WebSocketServerProtocolHandler("/hellowebsocket"));
        pipeline.addLast(new TextWebsocketFrameHandler());
    }
}

/**
 * 范型使用TextWebSocketFrame，Websocket传输使用Frame来传递的
 */
class TextWebsocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame>{
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        System.out.println("收到的消息:" + msg.text());
        ctx.channel().writeAndFlush(new TextWebSocketFrame("server发送websocket frame ,服务器时间：" + LocalDateTime.now()));
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println("handlerAdded: channel的全局id - " + ctx.channel().id().asLongText());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        System.out.println("handlerRemoved: channel的全局id - " + ctx.channel().id().asLongText());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("异常发生");
        ctx.close();
    }
}


