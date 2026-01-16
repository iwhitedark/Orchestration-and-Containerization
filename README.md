# Разработка и развертывание ИИ-приложения в Kubernetes

Итоговый проект «Разработка и развертывание ИИ-приложения в Kubernetes» по дисциплине "Оркестрация и контейнеризация". 1-й семестр 2-го курса МИФИ ИИКС РПО (2024-2026 уч. г).

## Описание проекта

Проект по развертыванию контейнерного Java-приложения для анализа тональности текста в Kubernetes с настройкой мониторинга, автомасштабирования и анализом современных тенденций в области AI и контейнеризации.

## Состав проекта

### Документация
- **Analytical_Report.docx** - Аналитический отчет
  - Анализ 5 статей с arXiv.org
  - Подробное описание архитектуры
  - Процесс развертывания
  - Выводы и рекомендации
  
- **Итоговый проект.pptx** - Презентация
  - Введение и цели
  - Архитектура решения
  - Развертывание
  - Мониторинг
  - Анализ трендов
  - Выводы

- **README.md** - Подробная инструкция по развертыванию

### Исходный код
- **SentimentAnalyzer.java** - REST API для анализа тональности
  - Эндпоинт: `/api/sentiment?text=<текст>`
  - Health check: `/health`
  - Metrics: `/metrics` (Prometheus format)

- **Dockerfile** - Multi-stage build
  - Базовый образ: eclipse-temurin:17-jre-alpine
  - Итоговый размер: <150 MB 

### Kubernetes манифесты
- **deployment.yaml** - Deployment с 3 репликами 
- **service.yaml** - LoadBalancer Service 
- **ingress.yaml** - Ingress для маршрутизации `/api` 
- **hpa.yaml** - Horizontal Pod Autoscaler (CPU 70%) 
- **servicemonitor.yaml** - Интеграция с Prometheus

### Скрипты автоматизации
- **01-install-minikube.sh** - Установка Minikube
- **02-start-cluster.sh** - Запуск кластера (4 CPU, 8GB RAM, 2 nodes)
- **03-build-image.sh** - Сборка Docker образа
- **04-deploy-app.sh** - Развертывание приложения
- **05-install-monitoring.sh** - Установка Prometheus + Grafana


## Быстрый старт

### Шаг 1: Установка Minikube
```bash
chmod +x 01-install-minikube.sh
./01-install-minikube.sh
```

### Шаг 2: Запуск кластера
```bash
chmod +x 02-start-cluster.sh
./02-start-cluster.sh
```
**Скриншот:** Сохраните вывод `minikube status` и `kubectl get nodes`

### Шаг 3: Сборка образа
```bash
chmod +x 03-build-image.sh
./03-build-image.sh
```
**Скриншот:** Размер образа должен быть <150 MB

### Шаг 4: Развертывание
```bash
chmod +x 04-deploy-app.sh
./04-deploy-app.sh
```
**Скриншоты:** 
- `kubectl get pods` (3 реплики)
- `kubectl get svc`
- `kubectl get ingress`
- `kubectl get hpa`

### Шаг 5: Мониторинг
```bash
chmod +x 05-install-monitoring.sh
./05-install-monitoring.sh
```
**Скриншоты:**
- Pods в namespace monitoring
- Grafana dashboard

### Шаг 6: Тестирование API
```bash
# Получить URL сервиса
minikube service sentiment-analyzer-service --url

# Тестирование
curl "http://<URL>/api/sentiment?text=great"
# Ожидаемый результат: {"text":"great","sentiment":"positive"}

curl "http://<URL>/api/sentiment?text=terrible" 
# Ожидаемый результат: {"text":"terrible","sentiment":"negative"}

curl "http://<URL>/health"
# Ожидаемый результат: {"status":"UP"}
```

### Шаг 7: Доступ к Grafana
```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
# Открыть: http://localhost:3000
# Логин: admin, Пароль: admin
```

---

## Архитектура

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP Request
       ▼
┌─────────────────┐
│ Ingress (nginx) │  → /api routing
└──────┬──────────┘
       │
       ▼
┌──────────────────────┐
│ Service LoadBalancer │  → Port 80:8080
└──────┬───────────────┘
       │
       ▼
┌────────────────────────┐
│ Deployment (3 Pods)    │
│ HPA: 3-10 replicas     │
│ CPU target: 70%        │
└──────┬─────────────────┘
       │
       ├──→ Prometheus ← /metrics
       │
       └──→ Grafana → Dashboard
```

---

## Мониторинг

### Ключевые метрики
- CPU utilization по подам
- Memory usage
- HTTP request rate
- HTTP response time
- Pod restart count
- HPA scaling events

### Дашборды Grafana
Импортируйте стандартные Kubernetes dashboards:
- ID: 315 (Kubernetes cluster monitoring)
- ID: 6417 (Kubernetes Deployment)


## Анализ трендов (5 статей)

### 1. Agentic AI Workflows (Декабрь 2024)
- Production-ready AI deployment
- Kubernetes + Docker + Prometheus
- Best practices для observability

### 2. Carbon-Aware Scheduling (Август 2024)
- Sustainability в data centers
- RL-based scheduling
- Прогноз: 12% энергопотребления к 2028

### 3. Security Landscape (Сентябрь 2024)
- 59% организаций испытали incidents
- NetworkPolicies, RBAC, Secret management
- Анализ 35,417 Stack Overflow постов

### 4. SynergAI (Сентябрь 2024)
- Edge-to-Cloud AI inference
- Architecture-aware scheduling
- >95% success rate

### 5. LLM Deployment (2024-2025)
- Large Language Models на Kubernetes
- MLOps workflow
- Kubeflow + MLflow integration

**Полный анализ:** См. Analytical_Report.docx

## Технологический стек

| Компонент | Технология | Версия |
|-----------|------------|--------|
| Язык программирования | Java | 17 (LTS) |
| Runtime | Eclipse Temurin JRE | 17-alpine |
| Контейнеризация | Docker | Latest |
| Оркестрация | Kubernetes (Minikube) | 1.28+ |
| Package Manager | Helm | 3.x |
| Мониторинг | Prometheus | 2.x |
| Визуализация | Grafana | 10.x |
| Ingress | NGINX | Latest |


## Выводы

Создано production-ready AI приложение  
Реализована полная CI/CD-ready архитектура  
Настроен comprehensive мониторинг  
Проанализированы актуальные тренды    
