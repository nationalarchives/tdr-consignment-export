FROM alpine:latest
RUN apk add --no-cache bash tar curl wget && \
    apk update && \
    apk upgrade p11-kit busybox libretls zlib openssl libcrypto3 libssl3 giflib && \
    adduser consignment-export -D && \
    wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -O /usr/local/bin/jq && \
    chmod +x /usr/local/bin/jq && \
    apk add openjdk17 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
WORKDIR /home/consignment-export
USER consignment-export
RUN wget https://truststore.pki.rds.amazonaws.com/eu-west-2/eu-west-2-bundle.pem
RUN curl -q https://api.github.com/repos/nationalarchives/tdr-consignment-export/releases/latest | jq -r '.assets[].browser_download_url' | xargs -I'{}' wget {}
RUN find ./ -name '*.tgz' -exec tar -xzf {} \; && mkdir export
CMD bash ./$COMMAND/bin/$COMMAND export --consignmentId $CONSIGNMENT_ID --taskToken $TASK_TOKEN_ENV_VARIABLE
