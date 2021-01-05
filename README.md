## 2021-survey-challenge
Production-ready REST API for a challenge.

- Used Spring Boot as a tried-and-tested framework for API production.
  - Common functionality for all services was expressed in generic classes. 
- An important design decision is whether to calculate score on read (backload) or write (frontload). This will depend on the read-write ratio.
  - At the moment, score calculation is done on read.
  - In the future, we can adapt to the read-write ratios of our use cases using variables in the query or environment level.
- Database-level calculations where possible instead of JVM, in favor of speed and scalability.
- Built-in metrics using Spring Actuator and Prometheus.
- Ready for containerization with Docker and deployment to AWS, Azure or Google Cloud.
- In-memory database as specified, H2 in this case.
    - For production, this will point to RDB solution in cloud platform of choice.
- API versioning would be done through content negotiation or custom headers, not URI path.
  - Query params, while possible, would complicate request routing.
  
### Usage
Local testing:
```
git clone https://github.com/JoseHdez2/2021-survey-challenge.git
gradlew bootRun
curl http:\\localhost:8080/products
```
Docker containerization:
```
gradlew bootBuildImage --imageName=[USERNAME]/2021-survey-challenge
```
