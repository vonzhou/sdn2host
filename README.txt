	
	SDN2Host
 实现Floodlight Controller和UDP Client的通信，进而控制传输行为

uclient.c 客户端
userver.c  服务器端
RedundancyMonitor.java  Floodlight模块


考虑：
1. 在FL中获得UDP 源端口是负值，所以不能利用，所以在客户端新开一个监听端口接收命令，和FTP类似。

