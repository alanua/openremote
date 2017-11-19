# Default deployment when manager-data volume is empty
FROM centos:7
RUN yum update -y && yum clean all
ADD deployment/custom /deployment