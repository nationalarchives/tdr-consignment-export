FROM alpine:latest
RUN apk add --no-cache bash tar curl wget && \
    apk update && \
    apk upgrade p11-kit busybox libretls zlib openssl libcrypto3 libssl3 giflib && \
    adduser consignment-export -D && \
    apk add openjdk17 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
WORKDIR /home/consignment-export
USER consignment-export
RUN wget https://truststore.pki.rds.amazonaws.com/eu-west-2/eu-west-2-bundle.pem
ARG VERSION
RUN test -n "$VERSION" || (echo "VERSION build arg is required" && exit 1)
RUN for app in tdr-consignment-export tdr-export; do \
      wget -q https://github.com/nationalarchives/tdr-consignment-export/releases/download/$VERSION/$app.tgz && \
      tar -xzf $app.tgz && \
      test -f ./$app/bin/$app || exit 1; \
    done && mkdir export
CMD bash ./$COMMAND/bin/$COMMAND export --consignmentId $CONSIGNMENT_ID --taskToken $TASK_TOKEN_ENV_VARIABLE
