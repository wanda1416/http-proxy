# ReadMe (HTTP Proxy)

# 中文

对于抓包需求来说，Fiddler、Wireshark 等抓包工具很好用，功能很强大，但是有时候显得会过于复杂。

如果只是简单地需要记录应用程序客户端和服务端的 HTTP 报文，用 Wireshark 就显得很复杂，处理起来也很麻烦。

本工具的作用就是把所有请求原样转发到另一个地址（保持 URL、header、body 不变），同时把**每一次**请求和响应完整地记录成一个独立的文本文件，放到指定目录里，直接打开就能看。

## 特性

- 单目标透明转发，保持 URL / header / body 不变（自动剔除 hop-by-hop 头）。
- 每个请求/响应事务写成一个独立文本文件，文件名带时间戳、序号、方法和路径，便于检索。
- 自动解压 `gzip` / `deflate` / `br` 响应体后再记录，彻底解决压缩数据看不到的问题。
- 文本内容（JSON / XML / HTML / text 等）内联到文件；二进制或超大 body 自动写入同名 `.bin` 旁挂文件。
- 控制台同时输出单行摘要（方法、路径、状态码、耗时、请求/响应字节数），方便实时观察。
- 可选 Basic 认证注入。
- 基于 OkHttp 连接池转发，性能可用。

## 使用方法

### 编译
```shell
mvn clean package
```

### 运行
```shell
java -jar target/http-proxy.jar \
  --server.port=8080 \
  --forward.url=http://127.0.0.1:7080 \
  --capture.dir=./capture
```

把客户端指向 `http://localhost:8080`，请求就会被转发到 `--forward.url`，同时在 `./capture` 目录下生成事务文件。

### 命令行参数

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `--forward.url` | 转发目标地址（必填） | `http://127.0.0.1:7080` |
| `--server.port` | 代理监听端口 | `8080` |
| `--capture.dir` | 事务文件输出目录 | `./capture` |
| `--capture.max-body` | 内联到文本的 body 上限（字节），超出写入 `.bin` | `1048576` |
| `--authorize` | 是否注入 Basic 认证 | `false` |
| `--username` / `--password` | Basic 认证凭据 | - |

## 注意事项

- 只处理 HTTP，不做 HTTPS MITM 解密。
- 单目标转发，不支持多路由。
- 输出目录会持续追加文件，请自行清理。


# English

For packet capture needs, Fiddler, Wireshark and other tools are powerful but sometimes too complex.

If you only need to record the HTTP messages between a client and a server, using Wireshark can be cumbersome.

This tool forwards every request unchanged to another address (keeping URL, headers and body intact) and records **each** request/response as a standalone text file in a directory you choose, so you can just open and read it.

## Features

- Transparent single-target forwarding, preserving URL / headers / body (hop-by-hop headers stripped).
- Each transaction is written to its own text file named with timestamp, sequence, method and path.
- `gzip` / `deflate` / `br` response bodies are decompressed before being recorded.
- Text content is inlined; binary or oversized bodies spill to a side-car `.bin` file.
- A single-line summary (method, path, status, latency, req/resp bytes) is printed to the console.
- Optional Basic authentication injection.
- Connection-pooled forwarding via OkHttp.

## Usage

### Compile
```shell
mvn clean package
```

### Run
```shell
java -jar target/http-proxy.jar \
  --server.port=8080 \
  --forward.url=http://127.0.0.1:7080 \
  --capture.dir=./capture
```

Point your client at `http://localhost:8080`; requests are forwarded to `--forward.url` and transaction files appear under `./capture`.

## Notice

- HTTP only; no HTTPS MITM decryption.
- Single-target forwarding, no multi-route support.
- The capture directory keeps growing; clean it up yourself.
