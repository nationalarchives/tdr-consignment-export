FROM alpine:3.19.1
RUN apk add --no-cache bash tar curl wget && \
    apk update && \
    apk upgrade p11-kit busybox libretls zlib openssl libcrypto3 libssl3 giflib && \
    adduser consignment-export -D && \
    wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 -O /usr/local/bin/jq && \
    chmod +x /usr/local/bin/jq && \
    apk add openjdk17 --repository=http://dl-cdn.alpinelinux.org/alpine/edge/community
WORKDIR /home/consignment-export
USER consignment-export
RUN wget $(curl https://api.github.com/repos/nationalarchives/tdr-consignment-export/releases/latest | jq -r '.assets[0].browser_download_url')
RUN tar -xzf ./tdr-consignment-export.tgz && mkdir export
CMD bash ./tdr-consignment-export/bin/tdr-consignment-export export --consignmentId $CONSIGNMENT_ID --taskToken $TASK_TOKEN_ENV_VARIABLE
