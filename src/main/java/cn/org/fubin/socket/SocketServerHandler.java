package cn.org.fubin.socket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.UUID;

/**
 * Created by fubin on 2019-07-25.
 */
public class SocketServerHandler extends SimpleChannelInboundHandler<String> {
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        //远程地址
        System.out.println(ctx.channel().remoteAddress()+","+msg);
        //处理完业务，把结果返回给客户端
        ctx.channel().writeAndFlush("from server:"+ UUID.randomUUID());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
        //出现异常，关闭连接
        ctx.close();
    }
}
