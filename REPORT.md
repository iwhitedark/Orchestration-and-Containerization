# Аналитический отчёт — тенденции ИИ и контейнеризации (arXiv, 2024–2025)

## Введение
Цель проекта: разработать контейнерное Java-приложение с REST API для анализа тональности текста и развернуть его в локальном кластере Kubernetes (Minikube), настроив балансировку, автоскейлинг и мониторинг (Prometheus + Grafana).
Дополнительно — провести обзор тенденций по тематике «AI + Kubernetes + containerization».

## Реализация приложения
REST API
- GET `/api/sentiment?text=...` -> JSON c полем `sentiment` (positive/negative/neutral)
- Метрики: `/actuator/prometheus`

## Контейнеризация
Использован multi-stage Dockerfile:
- сборка jar в `maven:...`
- рантайм в `distroless/java17-*` для уменьшения размера образа

## Развёртывание в Kubernetes
Deployment (3 реплики)
Файл: `k8s/10-deployment.yaml` — 3 реплики, probes, limits/requests.

Service (LoadBalancer) + Ingress
Файлы: `k8s/20-service.yaml`, `k8s/30-ingress.yaml`.
Ingress маршрутизирует префикс `/api` на сервис.

HPA по CPU
Файл: `k8s/40-hpa.yaml` — масштабирование 3..6 реплик при avg CPU 50%.

## Мониторинг (Prometheus + Grafana)
Prometheus/Grafana устанавливаются через Helm chart `kube-prometheus-stack`.
Сбор метрик приложения обеспечивается объектом ServiceMonitor (`k8s/50-servicemonitor.yaml`), который считывает `/actuator/prometheus`.

## Анализ тенденций (arXiv, 2024–2025)
Ниже — 3+ темы, которые чаще всего пересекаются в исследованиях на стыке AI и оркестрации.

Тренд 1: AI/RL-планирование (scheduler) вместо эвристик
Суть: вместо фиксированных эвристик в планировщиках и автоскейлерах используют модели ML/RL для более умного распределения нагрузки и ресурсов.
Пример статьи:
- Enhancing Kubernetes Automated Scheduling with Deep Learning and Reinforcement Learning (2024)
https://arxiv.org/abs/2403.07905
Вывод для проекта: HPA по CPU — базовый подход. Потенциальное улучшение — ML/RL-модель, учитывающая не только CPU, но и латентность, SLO, стоимость ресурсов и т.п.

Тренд 2: Serverless serving LLM at scale
Суть: рост LLM приводит к развитию serverless-подходов поверх Kubernetes: задача — уменьшать cold start, эффективно делить GPU/CPU, стабильно обслуживать запросы.
Пример статьи:
- DeepServe: Serverless Large Language Model Serving at Scale (2025)
https://arxiv.org/html/2501.14417v3
Вывод для проекта: даже простое REST-приложение можно масштабировать как микросервис; для LLM/инференса важнее становятся метрики latency и throughput, а не только CPU.

Тренд 3: Energy/Carbon-aware scheduling
Суть: оптимизация планирования с учётом энергопотребления и углеродного следа (особенно актуально из-за LLM).
Пример статьи:
- A Survey on Task Scheduling in Carbon-Aware Container ... (2025)
https://arxiv.org/pdf/2508.05949
Вывод для проекта: мониторинг можно расширять показателями энергии/CO2; перспективно — carbon-aware policies.

## Заключение
В рамках проекта создано и развёрнуто контейнерное приложение в Minikube, реализованы:
- балансировка (Service, Ingress),
- масштабирование (HPA),
- мониторинг (Prometheus, Grafana, ServiceMonitor),
а также выполнен обзор тенденций по 3+ работам arXiv.
