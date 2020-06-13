#!/bin/bash

sudo docker run -it --rm -p 127.0.0.1:4334-4336:4334-4336 gordonstratton/datomic-free-transactor:latest
