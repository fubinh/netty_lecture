package cn.org.fubin.netty.channel;

import cn.org.fubin.socket.SocketServerHandler;
import cn.org.fubin.socket.SocketServerInitalizer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by fubin on 2019-07-25.
 *
 * 测试 多线程写同一个channel消息是否有序
 *
 */
public class MutiWriteServer {

    public static void main(String[] args) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup,workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline channelPipeline = ch.pipeline();

                            channelPipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,0,4,0,4));
                            channelPipeline.addLast(new LengthFieldPrepender(4));
                            channelPipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                            channelPipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                            channelPipeline.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                    System.out.println("测试单个channel 多线程调用write...");
                                    final Channel channel = ctx.channel();
                                    final ByteBuf buf = Unpooled.copiedBuffer("your data", CharsetUtil.UTF_8);

                                    Runnable writer = new Runnable() {
                                        @Override
                                        public void run() {
                                            System.out.println("当前线程为："+Thread.currentThread().getName() + ";" + Thread.currentThread().getId());
                                            channel.writeAndFlush(buf.duplicate());
                                        }
                                    };

                                    Executor executor = Executors.newCachedThreadPool();
                                    executor.execute(writer);
                                    executor.execute(writer);
                                }
                            });
                        }
                    });

            ChannelFuture channelFuture = serverBootstrap.bind(8899).sync();
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}




