FROM golang:1.21-alpine AS builder

WORKDIR /app

# 安装依赖
RUN apk add --no-cache git

# 复制源代码
COPY go.mod go.sum ./
RUN go mod download

COPY . .

# 编译
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main ./cmd/server

# 最终镜像
FROM alpine:latest

WORKDIR /app

# 安装必要工具
RUN apk --no-cache add ca-certificates tzdata

# 复制编译产物
COPY --from=builder /app/main .

# 设置时区
ENV TZ=Asia/Shanghai

# 暴露端口
EXPOSE 8080

# 启动服务
CMD ["./main"]