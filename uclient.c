/*--vonzhou
  A small UDP client ...
 */

#include "global.h"

#define SERV_PORT 1234
#define CMD_PORT 4321

#define MAX 100

void recv_cmd(int sockfd, char *mesg){
		int rlen = 0;
		rlen = recvfrom(sockfd, mesg, sizeof(mesg), 0, NULL, NULL);
		//printf("%d\n", rlen);
		if(rlen < 0){
				printf("read sockfd error %s\n",strerror(errno));
				exit(-1);
		}
		//printf("CMD:%s\n", mesg);
}    
int main(int argc ,char *argv[])
{        
		char *fh;
		struct sysinfo s_info;
		long time1,time2;
		int error1,error2;
		int sockfd, cmd_sockfd;
		int res;
		struct stat fsize;
		struct sockaddr_in servaddr, controladdr;
		error1= sysinfo(&s_info);
		time1 = s_info.uptime;
		int r;
		char *buffer;
		char mesg[MAX];
		if(argc != 2){  
				printf("useage:udpclient <IPaddress>;\n");
				exit(-1);
		}
		bzero(&servaddr,sizeof(servaddr));
		servaddr.sin_family= AF_INET;
		servaddr.sin_port = htons(SERV_PORT);

		cmd_sockfd = socket(AF_INET,SOCK_DGRAM,0); /*create a socket for command */

		/*init servaddr*/
		bzero(&controladdr,sizeof(controladdr));
		controladdr.sin_family = AF_INET;
		controladdr.sin_addr.s_addr = htonl(INADDR_ANY);
		controladdr.sin_port = htons(CMD_PORT);

		//bind address and port to local control  socket*
		if(bind(cmd_sockfd,(struct sockaddr *)&controladdr,sizeof(controladdr)) == -1)
		{
				perror("bind error");
				exit(-1);
		}

		if(inet_pton(AF_INET,argv[1],&servaddr.sin_addr) <= 0){
				printf("[%s]is not a valid IPaddress\n",argv[1]);
				exit(-1);
		}

		sockfd =socket(AF_INET,SOCK_DGRAM,0);
		buffer = "Hello";
		r = sendto(sockfd, buffer, strlen(buffer), 0, (struct sockaddr *)&servaddr, 
						sizeof(struct sockaddr_in));   
		if(r < 0)
				perror("sendto failed ...");

		// get command from sdn controller ....
		memset(mesg, 0, MAX); 

		recv_cmd(cmd_sockfd, mesg);
		//printf("Receive command : %s\n", mesg);
		if(!strncmp(mesg, "ACK", 3)){
			buffer = "Redundant file. Stopped!";
			sendto(sockfd, buffer, strlen(buffer), 0, (struct sockaddr *)&servaddr, sizeof(struct sockaddr_in));
		}
		close(sockfd);
		close(cmd_sockfd);
}                
