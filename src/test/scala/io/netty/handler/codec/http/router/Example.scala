package io.netty.handler.codec.http.router

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelHandler, ChannelHandlerContext, ChannelInitializer, ChannelOption, SimpleChannelInboundHandler}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpVersion

object ExampleApp {
  def main(args: Array[String]) {
    Server.start(8000)
  }
}

object Server {
  def start(port: Int) {
    val bossGroup   = new NioEventLoopGroup(1)
    val workerGroup = new NioEventLoopGroup

    val b = new ServerBootstrap
    b.group(bossGroup, workerGroup)
     .childOption(ChannelOption.TCP_NODELAY,  java.lang.Boolean.TRUE)
     .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
     .channel(classOf[NioServerSocketChannel])
     .childHandler(PipelineInitializer)

    val ch = b.bind(port).sync().channel

    println("Server started: http://127.0.0.1:" + port + '/')
    ch.closeFuture.sync()

    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
  }
}

object PipelineInitializer extends ChannelInitializer[SocketChannel] {
  private val router = (new Router)
    .GET("/",             new RequestHandler)
    .GET("/articles/:id", classOf[RequestHandler])

  private val handler = new Handler(router)

  private val badClientSilencer = new BadClientSilencer

  def initChannel(ch: SocketChannel) {
    val p = ch.pipeline
      .addLast(new HttpServerCodec)
      .addLast(handler.name, handler)
      .addLast(badClientSilencer)
  }
}

@ChannelHandler.Sharable
class RequestHandler extends SimpleChannelInboundHandler[Routed] {
  override def channelRead0(ctx: ChannelHandlerContext, routed: Routed) {
    val content =
      s"req: ${routed.request()}\n" +
      s"path: ${routed.path()}, " +
      s"pathParams: ${routed.pathParams}, " +
      s"queryParams: ${routed.queryParams}"
    val res = new DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
      Unpooled.copiedBuffer(content.getBytes)
    )
    res.headers.set(HttpHeaders.Names.CONTENT_TYPE,   "text/plain")
    res.headers.set(HttpHeaders.Names.CONTENT_LENGTH, res.content.readableBytes)

    KeepAliveWrite.flush(ctx, routed.request(), res)
  }
}
