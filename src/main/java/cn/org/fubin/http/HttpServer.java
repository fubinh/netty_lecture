package cn.org.fubin.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.net.URI;

/**
 * Created by fubin on 2019/7/10.
 *
 * 最新的netty4.1.37.Final
 *
 * curl -X POST http://localhost:8899
 *
 */
public class HttpServer {
    public static void main(String[] args) throws Exception {
        //两个死循环，联想到Tomcat和操作系统的设计
        EventLoopGroup bossGroup = new NioEventLoopGroup();//接收连接
        EventLoopGroup workerGroup = new NioEventLoopGroup();//处理连接

        try{
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup,workerGroup)
                    .channel(NioServerSocketChannel.class)
                    //子处理器，自己编写的
                    .childHandler(new HttpServerInitializer());

            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        }finally {
            //优雅关闭
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}

/**
 * 初始化netty自带的处理器
 */
class HttpServerInitializer extends ChannelInitializer<SocketChannel>{
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        //一个管道，里面有很多拦截器，做业务处理
        ChannelPipeline pipeline = socketChannel.pipeline();
        //http处理器:http编解码的封装
        pipeline.addLast("httpServerCodec",new HttpServerCodec());
        pipeline.addLast("httpServerHandler",new HttpServerHandler());
    }
}

/**
 * 自己定义的处理器
 */
class HttpServerHandler extends SimpleChannelInboundHandler<HttpObject>{
    //读取客户端发送的请求，并且向客户端返回响应
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {

        //io.netty.handler.codec.http.DefaultHttpRequest
        System.out.println(httpObject.getClass());
        ///0:0:0:0:0:0:0:1:64406
        System.out.println(channelHandlerContext.channel().remoteAddress());
        Thread.sleep(8000);

        //不加服务端会抛异常
        if(httpObject instanceof HttpRequest){

            HttpRequest httpRequest = (HttpRequest)httpObject;
            System.out.println("请求方法名："+httpRequest.method().name());

            URI uri = new URI(httpRequest.uri());
            if("/favicon.ico".equals(uri.getPath())){
                System.out.println("请求favicon.ico");
                return;
            }

            //构造向客户端返回的字符串
            ByteBuf content = Unpooled.copiedBuffer("helloworld", CharsetUtil.UTF_8);
            //支持http响应的对象
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK,content);
            //设置http头信息
            response.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH,content.readableBytes());
            //响应发送给客户端
            channelHandlerContext.writeAndFlush(response);

            //主动关闭，会马上调用失效和注销事件
            channelHandlerContext.channel().close();
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel 激活");
    }
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel 注册");
    }
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel 失效");
    }
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        System.out.println("channel 注销");
    }
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        System.out.println("handler 添加");
    }
}
