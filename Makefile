# OpsChat Makefile
# AI Ops 智能运维助手 - 一键部署脚本

# 配置变量
SERVER_PORT ?= 8080
SERVER_URL = http://localhost:$(SERVER_PORT)
HEALTH_CHECK_API = $(SERVER_URL)/api/v1/health
REDIS_HOST ?= localhost
REDIS_PORT ?= 6379
MILVUS_HOST ?= localhost
MILVUS_PORT ?= 19530

DOCKER_COMPOSE_FILE = docker-compose.yml
MILVUS_CONTAINER = milvus-standalone

# 颜色输出
GREEN = \033[0;32m
YELLOW = \033[0;33m
RED = \033[0;31m
BLUE = \033[0;34m
NC = \033[0m

.PHONY: help deps up down start stop restart check status logs ps clean wait

help:
	@echo ""
	@echo "$(BLUE)╔═══════════════════════════════════════════════════════════════╗$(NC)"
	@echo "$(BLUE)║            OpsChat - AI Ops 智能运维助手                    ║$(NC)"
	@echo "$(BLUE)╚═══════════════════════════════════════════════════════════════╝$(NC)"
	@echo ""
	@echo "$(GREEN)📖 使用说明：$(NC)"
	@echo ""
	@echo "  $(YELLOW)快速开始：$(NC)"
	@echo "    make deps        启动依赖服务（Redis + Milvus）"
	@echo "    make start       启动 Spring Boot 服务"
	@echo ""
	@echo "  $(YELLOW)服务管理：$(NC)"
	@echo "    make start       启动 Spring Boot 服务（后台运行）"
	@echo "    make stop        停止 Spring Boot 服务"
	@echo "    make restart     重启 Spring Boot 服务"
	@echo "    make check       检查服务是否运行"
	@echo "    make status      查看所有服务状态"
	@echo ""
	@echo "  $(YELLOW)Docker管理：$(NC)"
	@echo "    make deps        启动依赖服务（Redis + Milvus）"
	@echo "    make up          启动 Docker 服务"
	@echo "    make down        停止 Docker 服务"
	@echo "    make logs        查看 Docker 日志"
	@echo "    make ps          查看容器状态"
	@echo ""
	@echo "  $(YELLOW)清理：$(NC)"
	@echo "    make clean       清理临时文件"
	@echo ""
	@echo "$(GREEN)🌐 服务地址：$(NC)"
	@echo "   API 服务: $(SERVER_URL)"
	@echo "   Redis: localhost:6379"
	@echo "   Milvus: localhost:19530"
	@echo "   Attu (Web UI): http://localhost:8000"
	@echo "   MinIO: http://localhost:9001 (admin/minioadmin)"
	@echo ""
	@echo "$(BLUE)═══════════════════════════════════════════════════════════════$(NC)"
	@echo ""

deps up:
	@echo "$(YELLOW)🐳 检查 Docker 容器状态...$(NC)"
	@if docker ps --format '{{.Names}}' | grep -q "^$(MILVUS_CONTAINER)$$"; then \
		echo "$(GREEN)✅ Milvus 容器已经在运行中$(NC)"; \
		echo "$(YELLOW)📋 当前运行的容器:$(NC)"; \
		docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"; \
	else \
		echo "$(YELLOW)🚀 启动 Docker Compose...$(NC)"; \
		docker compose -f $(DOCKER_COMPOSE_FILE) up -d; \
		echo ""; \
		echo "$(YELLOW)⏳ 等待容器启动...$(NC)"; \
		sleep 5; \
		if docker ps --format '{{.Names}}' | grep -q "^$(MILVUS_CONTAINER)$$"; then \
			echo "$(GREEN)✅ Docker Compose 启动成功！$(NC)"; \
			echo ""; \
			echo "$(GREEN)📋 运行中的容器:$(NC)"; \
			docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"; \
			echo ""; \
			echo "$(GREEN)🌐 服务访问地址:$(NC)"; \
			echo "   Milvus: localhost:19530"; \
			echo "   Attu (Web UI): http://localhost:8000"; \
			echo "   MinIO: http://localhost:9001 (admin/minioadmin)"; \
		else \
			echo "$(RED)❌ 容器启动失败，请检查日志: docker compose -f $(DOCKER_COMPOSE_FILE) logs$(NC)"; \
			exit 1; \
		fi; \
	fi

down:
	@echo "$(YELLOW)🛑 停止 Docker Compose...$(NC)"
	@if docker ps --format '{{.Names}}' | grep -q "milvus"; then \
		docker compose -f $(DOCKER_COMPOSE_FILE) down; \
		echo "$(GREEN)✅ Docker Compose 已停止$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  没有运行中的 Milvus 容器$(NC)"; \
	fi

start:
	@echo "$(YELLOW)🚀 启动 Spring Boot 服务...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)✅ 服务已经在运行中 ($(SERVER_URL))$(NC)"; \
	else \
		echo "$(YELLOW)📦 正在启动服务（后台运行）...$(NC)"; \
		nohup java -jar -Xms512m -Xmx1g target/OpsChat-1.0.0.jar > OpsChat.log 2>&1 & \
		echo $$! > OpsChat.pid; \
		echo "$(GREEN)✅ 服务启动命令已执行$(NC)"; \
		echo "$(YELLOW)   PID: $$(cat OpsChat.pid)$(NC)"; \
		echo "$(YELLOW)   日志文件: OpsChat.log$(NC)"; \
	fi

stop:
	@echo "$(YELLOW)🛑 停止 Spring Boot 服务...$(NC)"
	@if [ -f OpsChat.pid ]; then \
		pid=$$(cat OpsChat.pid); \
		if ps -p $$pid > /dev/null 2>&1; then \
			kill $$pid; \
			echo "$(GREEN)✅ 服务已停止 (PID: $$pid)$(NC)"; \
		else \
			echo "$(YELLOW)⚠️  进程不存在 (PID: $$pid)$(NC)"; \
		fi; \
		rm -f OpsChat.pid; \
	else \
		echo "$(YELLOW)⚠️  未找到 OpsChat.pid 文件$(NC)"; \
		pkill -f "OpsChat-1.0.0.jar" && echo "$(GREEN)✅ 已停止所有 OpsChat 进程$(NC)" || echo "$(YELLOW)⚠️  没有运行中的 OpsChat 进程$(NC)"; \
	fi

restart:
	@echo "$(YELLOW)🔄 重启 Spring Boot 服务...$(NC)"
	@echo ""
	@echo "$(YELLOW)步骤 1/2: 停止服务$(NC)"
	@$(MAKE) stop
	@echo ""
	@echo "$(YELLOW)步骤 2/2: 启动服务$(NC)"
	@$(MAKE) start
	@echo ""
	@$(MAKE) wait
	@echo ""
	@echo "$(GREEN)✅ 服务重启完成！$(NC)"

wait:
	@echo "$(YELLOW)⏳ 等待服务器就绪...$(NC)"
	@max_attempts=60; \
	attempt=0; \
	while [ $$attempt -lt $$max_attempts ]; do \
		if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
			echo "$(GREEN)✅ 服务器已就绪！($(SERVER_URL))$(NC)"; \
			exit 0; \
		fi; \
		attempt=$$((attempt + 1)); \
		printf "$(YELLOW)   等待中... [$$attempt/$$max_attempts]$(NC)\r"; \
		sleep 1; \
	done; \
	echo ""; \
	echo "$(RED)❌ 服务器启动超时！$(NC)"; \
	echo "$(YELLOW)请检查日志: tail -f OpsChat.log$(NC)"; \
	exit 1

check:
	@echo "$(YELLOW)🔍 检查服务器状态...$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "$(GREEN)✅ 服务器运行正常 ($(SERVER_URL))$(NC)"; \
	else \
		echo "$(RED)❌ 服务器未运行或无法连接！$(NC)"; \
		echo "$(YELLOW)请先启动项目: make start$(NC)"; \
		exit 1; \
	fi

status:
	@echo "$(YELLOW)📊 Docker 容器状态:$(NC)"
	@echo ""
	@if docker ps -a --format '{{.Names}}' | grep -q "milvus"; then \
		docker ps -a --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"; \
		echo ""; \
		running=$$(docker ps --filter "name=milvus" --format '{{.Names}}' | wc -l | tr -d ' '); \
		total=$$(docker ps -a --filter "name=milvus" --format '{{.Names}}' | wc -l | tr -d ' '); \
		echo "$(GREEN)运行中: $$running / $$total$(NC)"; \
	else \
		echo "$(YELLOW)⚠️  没有找到 Milvus 相关容器$(NC)"; \
		echo "$(YELLOW)提示: 运行 'make deps' 启动容器$(NC)"; \
	fi
	@echo ""
	@echo "$(YELLOW)📊 Spring Boot 服务状态:$(NC)"
	@if curl -s -f $(HEALTH_CHECK_API) > /dev/null 2>&1; then \
		echo "  $(GREEN)✅ 运行正常 ($(SERVER_URL))$(NC)"; \
	else \
		echo "  $(RED)❌ 未运行$(NC)"; \
	fi

logs:
	docker compose -f $(DOCKER_COMPOSE_FILE) logs -f

ps:
	docker compose -f $(DOCKER_COMPOSE_FILE) ps

clean:
	@echo "$(YELLOW)🧹 清理临时文件...$(NC)"
	@rm -f OpsChat.pid OpsChat.log
	@rm -rf target
	@echo "$(GREEN)✅ 清理完成$(NC)"

stop-all: stop down