FROM adoptopenjdk/openjdk16:jre16u-alpine-nightly
WORKDIR /app
COPY target/optimizer-1.0.2.jar /app/optimizer.jar
ENTRYPOINT ["java","-jar","optimizer.jar"]