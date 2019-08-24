package com.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * 使用 git 之后，当有新的配置更新时（假定这次更新叫做 A，更新内容是：age from 28 to 30），我们首先会将更新A push 到 git 上面去
 * 此时如果通过 config-server 的端口来访问 busdemo-dev.yml 文件的话，确实可以得到更新A，但是如果我们将监听 config-server 的 SpringCloudBusDemoApplication 启动后，
 * 访问它的启动端口例如10005：http://localhost:10005/actuator/env 时却发现无法得到更新A 的更新内容，这个时候可以通过访问 http://localhost:10005/actuator/refresh
 * 来将 SpringCloudBusDemoApplication 的配置刷新，再次访问 http://localhost:10005/actuator/env 就可以看到更新 A 的内容，但是这样的操作仅仅可以更改 /actuator/env 的
 * 数据，对于正在运行中的 SpringCloudBusDemoApplication，如果引用了更新 A 的更新内容 age，那么访问 SpringCloudBusDemoApplication 应用时得到的 age 还是 28，例如访问
 * SpringCloudBusDemoApplication 的 http://localhost:10005/consumer 
 * 
 * 对于这种热更新的情况，我们可以在 http://localhost:10005/consumer 请求对应的controller上面加上一个 @RefreshScope 注解，当 http://localhost:10005/actuator/env 
 * 的访问得到更新时，应用内的访问也会得到更新。
 * 
 * 总结：
 * 当修改配置文件提交到 git 上面去，作为配置中心 config-server（访问config-server：http://localhost:9002/busdemo/dev） 会第一时间收到更新，而依赖于配置中心的 http://localhost:10005/actuator/env
 * 需要通过 http://localhost:10005/actuator/refresh 手动刷新；而对于应用内的数据则需要通过在 controller 上添加 @RefreshScope 注解来使得应用的数据可以与 http://localhost:10005/actuator/env
 * 的数据同步更新
 * 
 * spring cloud bus 的 spring.factories 里面的 BusAutoConfiguration 使用 EventListener 定义了监听 RemoteApplicationEvent 类型事件的监听器 acceptLocal，该监听器收到事件之后就会将事件发送到 springCloudBusOutput 这个通道中
 * 同时也配置了 acceptRemote 来监听 springCloudBusInput 上面的流数据
 * 
 * 上面的过程需要人工的去对每个部署的应用执行一次应用端的请求：http://localhost:10005/actuator/refresh 实在是不方便，那有没有更方便的方式呢？
 * 引入 spring cloud bus+stream 的机制：当 config-server 也使用了 bus 机制后，会在开放端口中存在一个 bus-refresh 的端点，通过请求这个端点发布一个刷新的时间到 bus 上，另外一端的应用也要导入 bus 并且配置好 bus 通道，
 * 应用端接收到刷新时间后开始重新从 config-server 读取最新配置。
 * bus-refresh 主要是通过 RefreshBusEndpoint 这个类的 busRefreshWithDestination 方法来发布一个RefreshRemoteApplicationEvent 事件（也是 RemoteApplicationEvent 类型的事件），该事件将起到两个作用，一方面是触发了 BusAutoConfiguration 
 * 中配置的 acceptLocal 事件监听从而往消息通道中发送刷新事件，另外还触发了 RefreshListener 对于本地环境的刷新
 * 你可以通过调用 http://localhost:9002/actuator/bus-refresh 对所有监听 bus 应用进行更新，也可以调用 http://localhost:9002/actuator/bus-refresh/busdemo 来对单个应用进行配置更新
 * 
 * 以上还是需要人工的调用 config-server 端的配置，那么如何才能真正实现自动刷新配置呢？使用 git 上的 webhook
 * （1）当有人将配置文件push 到git 上之后，git会发出一个change的事件到配置中心提供的 bus-refresh 端点（需要配置 webhook）：http://localhost:9002/actuator/bus-refresh（post 请求）
 * （2）接下来就是重复上面的过程
 * 
 * 也可以通过发送 post 请求到 http://localhost:9002/monitor （需要加入 config-monitor jar包）并附上body：{"path":"busdemo"} 来发布一个刷新事件到 bus 上，之后的过程和上面 bus-refresh 一致
 * 具体可以查看 config-monitor jar包中 spring.factories 提到的 EnvironmentMonitorAutoConfiguration 配置以及 PropertyPathEndpoint 这个Controller的作用
 * 
 * @author A0225204
 *
 */
@SpringBootApplication
@EnableConfigServer
public class SpringCloudConfigServerDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudConfigServerDemoApplication.class, args);
	}

}
