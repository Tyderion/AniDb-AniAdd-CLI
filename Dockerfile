FROM amazoncorretto:22.0.1-al2023-headless
MAINTAINER Tyderion


RUN yum install -y findutils
RUN mkdir /app
COPY ./build/libs/aniadd-cli-all-3.0.jar /app/aniadd-cli.jar
COPY ./run.sh /app/run.sh
CMD ["/app/run.sh"]