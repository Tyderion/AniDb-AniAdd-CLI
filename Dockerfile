FROM amazoncorretto:22.0.1-al2023-headless
MAINTAINER Tyderion

RUN mkdir /app
COPY ./build/libs/aniadd-all-cli-1.0-SNAPSHOT.jar /app/aniadd-cli.jar
COPY ./run.sh /app/run.sh
CMD ["/app/run.sh"]