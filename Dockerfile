FROM debian:bookworm
WORKDIR /opt/cessda/cdc/validator/

# Container Information
LABEL maintainer='CESSDA-ERIC "support@cessda.eu"'

# Copy compiled application and libraries
COPY target/*.so ./
COPY target/validator ./

ENTRYPOINT ["/opt/cessda/cdc/validator/validator"]