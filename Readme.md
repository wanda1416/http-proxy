# ReadMe (HTTP Proxy)

# 中文

对于抓包需求来说， Fiddler、Wireshark 等抓包工具很好用，功能很强大，但是有时候显得会过于复杂。

如果只是简单的需要记录应用程序客户端和服务端的 http 报文，用 Wireshark 就显得很复杂，处理起来也很麻烦。

本工具作用就是实现了将所有的请求直接转发到另一个地址上，保持全部 http 请求 url、 header 和 body 不变。

同时完整的记录 http 的 request 和 response 全部内容到日志中。

## 使用方法

### 编译
```shell
mvn clean package
```

### 使用
```shell
java -jar http-proxy.jar
```

## 注意事项
 
HTTP response 目前只会记录 xml、json、text 等文本内容，且无法处理压缩数据。


# English

For packet capture needs, Fiddler, Wireshark and other packet capture tools are very useful and powerful, but sometimes they can be too complicated.

If you only need to record the HTTP messages between the application client and the server, using Wireshark can be very complex and cumbersome to handle.

This tool allows all requests to be forwarded directly to another address, while keeping all HTTP request URLs, headers, and bodies unchanged. At the same time, the complete contents of the HTTP request and response are recorded in the log.


## Usage

### Compile
```shell
mvn clean package
```

### Use
```shell
java -jar http-proxy.jar
```

## Notice

Note that currently only text-based content such as XML, JSON, and text will be recorded in the HTTP response, and compressed data cannot be processed.



