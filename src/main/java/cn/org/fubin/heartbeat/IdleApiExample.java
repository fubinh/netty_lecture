package cn.org.fubin.heartbeat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

/**
 * Created by fubin on 2019/7/14.
 */
public class IdleApiExample {
    public static void main(String[] args) {

    }
}

class MyChannelInitializer extends ChannelInitializer<Channel>{
    @Override
    protected void initChannel(Channel ch) {
        ch.pipeline().addLast("idleStateHandler",new IdleStateHandler(60,30,0));
        ch.pipeline().addLast("myHandler",null);
    }
}

class MyHandler extends ChannelDuplexHandler{
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            IdleStateEvent e = (IdleStateEvent)evt;
            if(e.state() == IdleState.READER_IDLE){
                ctx.close();
            }else if(e.state() == IdleState.WRITER_IDLE){
                ctx.writeAndFlush("写空闲！");
            }
        }
    }
}
