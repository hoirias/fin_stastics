# Intensive Lv2. TeamC_송승일

음식을 주문하고 요리하여 배달하는 현황을 확인 할 수 있는 CNA의 개발.</br>
개인 프로젝트로 주문내역을 기반으로 식당별 주문량을 기반으로 인기있는 식당을 구별 할 수 있게 한다.

# Table of contents

- [Restaurant](# )
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현)
    - [DDD 의 적용](#ddd-의-적용)
    - [동기식 호출 과 Fallback 처리](#동기식-호출과-Fallback-처리)
    - [비동기식 호출과 Saga Pattern](#비동기식-호출과-Saga-Pattern)
    - [Gateway](#Gateway)
    - [CQRS](#CQRS)
  - [운영](#운영)
    - [AWS를 활용한 코드 자동빌드 배포 환경구축](#AWS를-활용한-코드-자동빌드-배포-환경구축)
    - [서킷 브레이킹과 오토스케일](서킷-브레이킹과-오토스케일)
    - [무정지 배포](#무정지-배포)
    - [마이크로서비스 로깅 관리를 위한 PVC 설정](#마이크로서비스-로깅-관리를-위한-PVC-설정)
    - [SelfHealing](#SelfHealing)
  - [첨부](#첨부)

# 서비스 시나리오

음식을 주문하고, 요리현황 및 배달현황을 조회.</br>
주문내역을 기반으로 식당별 주문량을 조회하여 인기있는 식당을 판별.

## 기능적 요구사항

1. 주문결과를 기반으로 식당별 인기를 가늠하기 위한 주문 수량을 누적 관리하는 통계시스템의 개발.
1. 식당별 주문 횟수를 Mypage에서 확인.
1. 비정상적인 주문으로 판단될 경우 특이내역 Status를 주문 단계로 보내준다.

## 비기능적 요구사항
1. 장애격리
    1. 통계 시스템이 과중되면 잠시동안 통계 input을 받지 않도록 한다. 
    1. 통계 시스템이 죽을 경우 재기동 될 수 있도록 한다.
1. 운영
    1. 통계 시스템이 과중되면 Replica를 추가로 띄울 수 있도록 한다.

# 분석/설계

## Event Storming 결과
* MSAEz 로 모델링한 이벤트스토밍 결과 : http://www.msaez.io/#/storming/t5Z5EXdDP0UOZDvGzeNH61hF8qG3/mine/31d6e46b00cfe0fde39144c1d94d53e9/-MHFudGR0LDzQZSMO1tV
![msaez](https://user-images.githubusercontent.com/54210936/93413124-81f30300-f8d9-11ea-8cd4-7eb989035517.png)

### 이벤트 도출
1. 주문됨
1. 주문한 식당과 메뉴를 기반으로 통계자료를 생성한다.
1. 주문이 취소되면 통계자료에서 삭제한다.
1. 통계 생성시 특정 이상이 감지되면 주문을 취소한다.


### 어그리게잇으로 묶기

  * 고객의 주문(Order), 식당의 요리(Cook), 배달(Delivery)은 그와 연결된 command와 event 들에 의하여 트랙잭션이 유지되어야 하는 단위로 묶어 줌.
  * 개인 프로젝트에서는 주문(Order)를 기반으로 통계자료(Statistics)를 생성하는 어그리게잇을 추가해준다.

### Policy 부착 

### Policy와 컨텍스트 매핑 (점선은 Pub/Sub, 실선은 Req/Res)

### 기능적 요구사항 검증
 * 고객이 메뉴를 주문한다.(ok)
 * 고객은 본인의 주문을 취소할 수 있다.(ok)
 * 고객이 메뉴를 주문하면 식당과 메뉴를 기반으로 통계를 생성한다.(ok)
 * 고객이 주문을 취소하면 통계에서도 데이터를 삭제한다.(ok)
 * 통계 데이터에 이상이 감지되면(특정 숫자이상의 주문) 주문을 취소한다.(ok)
 * 고객은 Mypage를 통해, 어느식당의 메뉴가 인기 있는지 조회할 수 있다.(ok)

</br>
</br>



# 구현:

분석/설계 단계에서 도출된 아키텍처에 따라, 각 BC별로 마이크로서비스들을 스프링부트 + JAVA로 구현하였다. 각 마이크로서비스들은 Kafka와 RestApi로 연동되며 스프링부트의 내부 H2 DB를 사용한다.


## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (통계의 마이크로서비스 ).

```
package myProject_LSP;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import java.util.List;

@Entity
@Table(name="Statistics_table")
public class Statistics {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long orderId;
    private Long restaurantId;
    private Long value;    
    ....
}
```
- JPA를 활용한 Repository Pattern을 적용하여 이후 데이터소스 유형이 변경되어도 별도의 처리 없이 사용 가능한 Spring Data REST 의 RestRepository 를 적용하였다
```
package myProject_LSP;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface StatisticsRepository extends PagingAndSortingRepository<Statistics, Long>{
    Optional<Statistics> findByRestaurantId(Long RestaurantId);
}
```
</br>

## 동기식 호출과 Fallback 처리

분석단계에서의 조건 중 하나로 주문->취소 간의 호출은 트랜잭션으로 처리. 호출 프로토콜은 Rest Repository의 REST 서비스를 FeignClient 를 이용하여 호출.
- 주문(Order)이 발생 했을 때, 통계 서비스를 호출하기 위해 Stub함수와 FeignClient 를 이용하여 인터페이스(Proxy)를 구현 

```
@FeignClient(name="statistics", url="${api.url.statistics}")
public interface StatisticsService {

  @RequestMapping(method= RequestMethod.POST, path="/statistics")
  public void statisticsSend(@RequestBody Statistics statistics);

}
```

- 주문이 접수 될 경우 통계(Stastics)에 주문내역을 추가 해준다.
```
@PrePersist
public void onPrePersist(){
   Statistics statistics = new Statistics();
   BeanUtils.copyProperties(this, statistics);  
   stastics.setValue(this.getValue()+statistics.getValue());    
   stastics.publishAfterCommit();   
```

</br>

## 비동기식 호출과 Saga Pattern

통계 처리 중, 주문수량 등에 특이성이 발견 될 경우 주문(Order) ID에 STATUS를 기록하는 publish를 발행한다.  
 
```
# 주문시 통계내역을 조회하는 로직
@Entity
@Table(name="Stastics_table")
public class Stastics {
    private boolean flowchk = true;
    ...
    @PostPersist
    public void onPostPersist(){
        if(flwchk) {        ### 특이 주문이 아닐 경우 주문횟수를 카운팅 해준다.
            Statistics statistics = new Statistics();
            BeanUtils.copyProperties(this, statistics);
            statistics.setValue(stastics.getValue()++);
            statistics.publishAfterCommit();
        }
    }

    @PrePersist
    public void onPrePersist(){
        if(this.getValue() > 10) {    ### 특이 주문이 들어올 경우 Order로 특이 주문 값을 publish
            OrderStatisticsed orderStatisticsed = new OrderStatisticsed();
            BeanUtils.copyProperties(this, orderStatisticsed);
            orderStatisticsed.publishAfterCommit();
            flwchk = false;
        }
    }
}

...
# 주문서비스의 status를 변경 연동 설정
@Autowired
OrderRepository orderRepository;

@StreamListener(KafkaProcessor.INPUT)
public void wheneverOrderStatisticsed_OrderStatisticsedUpdate(@Payload OrderStatisticsed orderStatisticsed){
  if(orderStatisticsed.isMe()){
      Optional<Order> orderOptional = orderRepository.findById(OrderStatisticsed.getOrderId());
      Order order = orderOptional.get();
      order.setStatus("ORDER : ORDER CHK");
      orderRepository.save(order);
  }
}
```

</br>

## Gateway
하나의 접점으로 서비스를 관리할 수 있는 Gateway를 통한 서비스라우팅을 적용 한다. Loadbalancer를 이용한 각 서비스의 접근을 확인 함.

```
# Gateway 설정(https://github.com/hoirias/fin_gateway/blob/master/target/classes/application.yml)
spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/**
        - id: cook
          uri: http://cook:8080
          predicates:
            - Path=/cooks/**,/cancellations/**
        - id: delivery
          uri: http://delivery:8080
          predicates:
            - Path=/deliveries/**
        - id: mypage
          uri: http://mypage:8080
          predicates:
            - Path= /mypages/**
        - id: statistics
          uri: http://statistics:8080
          predicates:
            - Path= /statisticses/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```
![gateway1](https://user-images.githubusercontent.com/54210936/93407972-6df5d400-f8ce-11ea-8145-60dcd6a65188.png)
![gateway2](https://user-images.githubusercontent.com/54210936/93407973-6df5d400-f8ce-11ea-9b90-44d741445dff.png)

</br>

## CQRS
기존 코드에 영향도 없이 mypage 용 materialized view 구성한다. 한개의 페이지에서 확인 할 수 있게 됨.</br>
```
# 주문 내역 mypage에 insert
   @StreamListener(KafkaProcessor.INPUT)
    public void whenOrdered_then_CREATE_1 (@Payload Ordered ordered) {
        try {
            if (ordered.isMe()) {
                // view 객체 생성
                Mypage mypage = new Mypage();
                // view 객체에 이벤트의 Value 를 set 함
                mypage.setRestaurantId(ordered.getRestaurantId());
                mypage.setRestaurantMenuId(ordered.getRestaurantMenuId());
                mypage.setCustomerId(ordered.getCustomerId());
                mypage.setQty(ordered.getQty());
                mypage.setOrderId(ordered.getId());
                mypage.setOrderStatus(ordered.getStatus());
                // view 레파지 토리에 save
                mypageRepository.save(mypage);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

# 요리내역(Cook) mypage 업데이트
    @StreamListener(KafkaProcessor.INPUT)
    public void whenCooked_then_UPDATE_1(@Payload Cooked cooked) {
        try {
            if (cooked.isMe()) {
                // view 객체 조회
                List<Mypage> mypageList = mypageRepository.findByOrderId(cooked.getOrderId());
                for(Mypage mypage : mypageList){
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    mypage.setCookId(cooked.getId());
                    mypage.setCookStatus(cooked.getStatus());
                    // view 레파지 토리에 save
                    mypageRepository.save(mypage);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
 ```
![cqrs](https://user-images.githubusercontent.com/54210936/93281210-987c5a00-f806-11ea-835b-2cea09bf6466.png)

</br>
</br>


# 운영

## AWS를 활용한 코드 자동빌드 배포 환경구축

  * AWS codebuild를 설정하여 github이 업데이트 되면 자동으로 빌드 및 배포 작업이 이루어짐.
  * Github에 Codebuild를 위한 yml 파일을 업로드하고, codebuild와 연동 함
  * 각 마이크로서비스의 build 스펙
  ```
    https://github.com/hoirias/fn-order/blob/master/buildspec.yml
    https://github.com/hoirias/fn-cook/blob/master/buildspec.yml
    https://github.com/hoirias/fn-delivery/blob/master/buildspec.yml
    https://github.com/hoirias/fn-gateway/blob/master/buildspec.yml
    https://github.com/hoirias/fn-mypage/blob/master/buildspec.yml
    https://github.com/hoirias/fn-stastics/blob/master/buildspec.yml
  ```
  
</br>

## 서킷 브레이킹과 오토스케일

* 서킷 브레이킹 :
주문이 과도하여 통계시스템에 부하가 걸릴 경우 CB 를 통하여 장애격리. 500 에러가 5번 발생하면 10분간 CB 처리하여 100% 접속 차단
```
# AWS codebuild에 설정(https://github.com/hoirias/fn-stastics/blob/master/buildspec.yml)
 http:
   http1MaxPendingRequests: 1   # 연결을 기다리는 request 수를 1개로 제한 (Default 
   maxRequestsPerConnection: 1  # keep alive 기능 disable
 outlierDetection:
  consecutiveErrors: 1          # 5xx 에러가 5번 발생하면
  interval: 1s                  # 1초마다 스캔 하여
  baseEjectionTime: 10m         # 10분 동안 circuit breaking 처리   
  maxEjectionPercent: 100       # 100% 로 차단
```

* 오토스케일(HPA) :
CPU사용률 10% 초과 시 replica를 5개까지 확장해준다. 상용에서는 70%로 세팅하지만 여기에서는 기능적용 확인을 위해 수치를 조절.
```
apiVersion: autoscaling/v1
kind: HorizontalPodAutoscaler
metadata:
  name: skcchpa-statistics
  namespace: hoirias
  spec:
    scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: $_PROJECT_NAME                # order (주문) 서비스 HPA 설정
    minReplicas: 1                      # 최소 1개
    maxReplicas: 5                      # 최대 5개
    targetCPUUtilizationPercentage: 10  # cpu사용율 10프로 초과 시 
```    
* 부하테스트(Siege)를 활용한 부하 적용 후 서킷브레이킹 / 오토스케일 내역을 확인한다.
![siege_1](https://user-images.githubusercontent.com/54210936/93409676-5d475d00-f8d2-11ea-8c54-5c2c9164e993.png)
![replica](https://user-images.githubusercontent.com/54210936/93410779-997bbd00-f8d4-11ea-96dc-64f3351f1f47.png)

</br>

## 무정지 배포

* 무정지 배포를 위해 ECR 이미지를 업데이트 하고 이미지 체인지를 시도 함. Github에 소스가 업데이트 되면 자동으로 AWS CodeBuild에서 컴파일 하여 이미지를 ECR에 올리고 EKS에 반영.
  이후 아래 옵션에 따라 무정지 배포 적용 된다.
  

```
# AWS codebuild에 설정(https://github.com/fn-stastics/blob/master/buildspec.yml)
  spec:
    replicas: 5
    minReadySeconds: 10   # 최소 대기 시간 10초
    strategy:
      type: RollingUpdate
      rollingUpdate:
      maxSurge: 1         # 1개씩 업데이트 진행
      maxUnavailable: 0   # 업데이트 프로세스 중에 사용할 수 없는 최대 파드의 수

```

- 새버전으로의 배포 시작(V3로 배포)
![imagechange_0](https://user-images.githubusercontent.com/54210936/93409084-0bea9e00-f8d1-11ea-988c-dc8c96fa7492.png)

- siege를 이용한 부하 적용. Availability가 100% 미만으로 떨어짐. 쿠버네티스가 새로 올려진 서비스를 Ready 상태로 인식하여 서비스 유입을 진행 하였음. Readiness Probe 설정하여 조치 필요.
![siege_3](https://user-images.githubusercontent.com/54210936/93412876-fc6f5300-f8d8-11ea-836d-809ea5bb16ed.png)

- 새버전 배포 확인(V3 적용)
![imagechange_3](https://user-images.githubusercontent.com/54210936/93412131-86b6b780-f8d7-11ea-92df-3621ed74d4eb.png)


- Readiness Probe 설정을 통한 ZeroDownTime 설정.
```
  readinessProbe:
    tcpSocket:
      port: 8080
      initialDelaySeconds: 180      # 서비스 어플 기동 후 180초 뒤 시작
      periodSeconds: 120            # 120초 주기로 readinessProbe 실행 
```
![ZeroDownTime  SEIGE_STATUS_read](https://user-images.githubusercontent.com/54210936/93278989-1473a380-f801-11ea-8140-f7edbc2c9b6f.jpg)


</br>

## 마이크로서비스 로깅 관리를 위한 PVC 설정
AWS의 EFS에 파일시스템을 생성(admin12-efs (fs-314a5510))하고 서브넷과 클러스터(admin12-final)를 연결하고 PVC를 설정해준다. 각 마이크로 서비스의 로그파일이 EFS에 정상적으로 생성되고 기록됨을 확인 함.
```
#AWS의 각 codebuild에 설정(https://github.com/hoirias/fn-stastics/blob/master/buildspec.yml)
volumeMounts:  
- mountPath: "/mnt/aws"    # 서비스 로그파일 생성 경로
  name: volume                 
volumes:                   # 로그 파일 생성을 위한 EFS, PVC 설정 정보
- name: volume
  persistentVolumeClaim:
  claimName: aws-efs  
```
![efs](https://user-images.githubusercontent.com/54210936/93411335-e318d780-f8d5-11ea-829a-28ee69973691.png)

</br>

## SelfHealing
운영 안정성의 확보를 위해 마이크로서비스가 아웃된 뒤에 다시 프로세스가 올라오는 환경을 구축한다. 
log 파일을 삭제하여 어플리케이션을 죽이고, 프로세스가 재기동 됨을 확인 함.
```
#AWS의 각 codebuild에 설정(https://github.com/hoirias/fn-stastics/blob/master/buildspec.yml)
livenessProbe:
  tcpSocket:
  port: 8080
  initialDelaySeconds: 20     # 서비스 어플 기동 후 20초 뒤 시작
  periodSeconds: 3            # 3초 주기로 livenesProbe 실행 
```
![selfhealing](https://user-images.githubusercontent.com/54210936/93412203-ae0d8480-f8d7-11ea-8acf-329acfe7c789.png)
![efs2](https://user-images.githubusercontent.com/54210936/93411886-0ee88d00-f8d7-11ea-93c7-8e4af7e394ef.png)

</br>
</br>

