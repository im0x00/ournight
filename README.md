# OurNight
> 어떤 프로덕트인지 직관적 이해를 위해서는 ournight; product를 참고하자.

대학 축제 주점을 위한 QR 주문 · POS 플랫폼. 한양대 2026 라치오스 주점 2곳에서 처음 실제로 쓰였다.

이 repo의 중심은 **POS 알림(notification) 서비스**다. 손님의 주문, hall의 확인, kitchen의 조리 완료가 서로 다른 화면에 1초 안에, 같은 store 안에서는 순서대로 떠야 한다. 같은 hub를 두 runtime(Spring MVC + virtual thread, WebFlux + coroutine)으로 직접 짜고, 같은 부하에서 벽을 찾아 비교했다.

설계와 문제 해결 기록은 전부 [im0x00.com](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3)에 있다.


## 문서

| 내용 | 링크 |
| --- | --- |
| 무엇을 왜 만들었나: 문제, core loop, order 상태, role | [Product](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/1563fbc9-25cf-42a3-8507-82ba0d7f033b) |
| 축제 현장에 어떻게 퍼뜨렸나 | [Distribution](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/4b1701b3-e7b3-4f8f-9f5e-304917e7f6a7) |
| 알림 서비스 설계: SLO, SSE · broker · outbox relay 선택, scalability, 고려해볼 문제 | [HLD: Notification Service](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/ee0a5fc7-98fa-466d-aa2d-eb7446b14971) |
| hub 구현 1: Spring MVC + virtual thread, store마다 channel + lock | [Hub — MVC & VT](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/66358ccdcc596586) |
| hub 구현 2: WebFlux + coroutine, store마다 owner coroutine + inbox | [Hub — WebFlux & coroutine](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/23579a64-293a-4e6c-8d4d-055020c81a3b) |
| 두 구현 비교: 같은 부하에서 latency, correctness, 자원, 한계 | [Hub comparison — MVC vs WebFlux](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/9d4f9b25-31a4-45ae-9e3e-9039dd7a97ff) |

## 구조

| 폴더 | 내용 | 관련 문서 |
| --- | --- | --- |
| `OurNight_BE/` | Spring Boot + Kotlin API 서버. 주문 · POS · outbox relay · SSE, terraform | [HLD](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/ee0a5fc7-98fa-466d-aa2d-eb7446b14971) |
| `notification-mvc-vt/` | hub 구현 1 (MVC + virtual thread) | [Hub — MVC & VT](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/66358ccdcc596586) |
| `notification-webflux-coroutine/` | hub 구현 2 (WebFlux + coroutine) | [Hub — WebFlux & coroutine](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/23579a64-293a-4e6c-8d4d-055020c81a3b) |
| `notification-benchmark/` | load generator, compose (Redis + 각 hub를 같은 CPU · memory로) | [Hub comparison](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/9d4f9b25-31a4-45ae-9e3e-9039dd7a97ff) |
| `OurNight_FE/` | 손님 QR 웹 · POS 화면 | [Product](https://im0x00.com/#/x/d4cc4306-3e9e-43f1-bb7b-b3f970d859b3/1563fbc9-25cf-42a3-8507-82ba0d7f033b) |

## 실행

```bash
# API 서버
cd OurNight_BE && docker compose up -d && ./gradlew bootRun

# hub benchmark (notification-* module은 따로 된 Gradle build)
cd notification-benchmark
docker compose up -d redis
./gradlew :notification-mvc-vt:bootRun   # 또는 :notification-webflux-coroutine:bootRun
```
