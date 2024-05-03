# Java spring boot docker kubernetes shopping microservice with AWS
Deploying java spring boot shopping microservices with Docker, Kubernetes and AWS

### Microservices:
- Productcatalogue: Microservice to manage product catalogue
- Shopfront: Microservice to show ui for shopping
- Stockmanager: Microservice to manage stock

### Prerequisites
- Java 8
- Maven
- Docker
- Kubernetes
- AWS Account and AWS CLI
- Your AWS CLI must have appropriate permissions to create ECR and EKS resources

### Build Docker images
- Clean and install productcatalogue and stockmanager using maven
```sh
$ cd productcatalogue && mvn clean install
$ cd shopfront && mvn clean install
$ cd stockmanager && mvn clean install
```

- Clean and install shopfront using maven
  - In the shopfront project, the urls of productcatalogue and stockmanager are environment variables. So, we need to initialize them before building the project.
```sh
Initiliaze the environment variables of shopfront project in local machine

$ export PRODUCT_CATALOGUE_URL=http://productcatalogue:8020
$ export STOCK_MANAGER_URL=http://stockmanager:8030

$ cd shopfront && mvn clean install
```

- Build Docker images for all microservices
```sh
Update v1 in the below commands to the version you want to use

$ docker build -t shopping-productcatalogue:v1 .
$ docker build -t shopping-shopfront:v1 .
$ docker build -t shopping-stockmanager:v1 .
```

### AWS ECR (Elastic Container Registry)
ECR is a fully-managed Docker container registry that makes it easy for developers to store, manage, and deploy Docker container images. Amazon ECR is integrated with Amazon Elastic Container Service (ECS), simplifying your development to production workflow.

In this project, we will use AWS ECR to store our Docker images.
- Create ECR repository using AWS CLI
```sh
$ aws ecr create-repository --repository-name ecommerce-app-repo --region eu-west-1 
```
- Connect to ECR
```sh
You can get the link from AWS ECR console
$ aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com
```
- Tag and push the Docker images to ECR
```sh
$ docker tag productcatalogue:v1 <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:productcatalogue-v1
$ docker push <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:productcatalogue-v1

$ docker tag shopfront:v1 <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:shopfront-v1
$ docker push <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:shopfront-v1

$ docker tag stockmanager:v1 <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:stockmanager-v1
$ docker push <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:stockmanager-v1
```

### Create Kubernetes Cluster using EKS
Amazon Elastic Kubernetes Service (Amazon EKS) is a managed service that makes it easy for you to run Kubernetes on AWS without needing to install, operate, and maintain your own Kubernetes control plane.

In this project, we will use Amazon EKS to create a Kubernetes cluster.
- Create EKS cluster using AWS CLI
```sh
$ eksctl create cluster --name ecommerce-app --region eu-west-1 --version 1.29 --nodes=1 --node-type=t2.small
```
- Configure yaml files deployment and service using template below:
- These files are already configured in the project, in the kubernetes folder
  - productcatalogue-service.yaml
  - shopfront-service.yaml
  - stockmanager-service.yaml
  - NB: Update information in the yaml files like name, image, port, targetPort etc according to each microservice
  - NB: Project ports shopfront(8010), productcatalogue(8020), stockmanager(8030)
```yaml
---
apiVersion: v1 # version of the API
kind: Service # type of resource: Service in this case
metadata:
  name: stockmanager # name of the resource
  labels:
    app: stockmanager # labels to identify the resource
spec:
  type: LoadBalancer # type of service load balancer in this case
  selector:
    app: stockmanager # selector to identify the pods
  ports:
    - protocol: TCP # protocol to use
      port: 80 # port to expose, it's also the port of load balancer
      targetPort: 8030 # port of the stockmanager microservice present in stockmanager/src/main/resources/application.properties
---
apiVersion: apps/v1 # version of the API
kind: Deployment # type of resource: Deployment in this case
metadata:
  name: stockmanager # name of the resource
  labels:
    app: stockmanager # labels to identify the resource
spec: # specification of the resource
  replicas: 2 # number of pods to create, in this case, we will create 2 pods
  selector: # selector to identify the pods
    matchLabels: # labels to match
      app: stockmanager # labels to identify the resource
  template: # template to create the pods
    metadata: # metadata of the pod
      labels: # labels to identify the pod
        app: stockmanager # labels to identify the resource
    spec: # specification of the pod
      containers:  # containers to run in the pod, docker container in this case
        - name: stockmanager # name of the container
          image: <aws_account_id>.dkr.ecr.eu-west-1.amazonaws.com/ecommerce-app-repo:productcatalogue-v1 # image of the docker container built and pushed to AWS ECR
          ports: # ports to expose
            - containerPort: 8030 # port of the stockmanager microservice present in stockmanager/src/main/resources/application.properties
```
- **IMPORTANT: The load balancer port for every microservice is the same 80, the requests will be forwarded to the respective microservice based on the targetPort**
- Configure configmap for the environment variables in shopfront: **ecommerce-app-config.yaml**
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ecommerce-app-config
data:
  productcatalogue-url: http://productcatalogue:80
  stockmanager-url: http://stockmanager:80
```
- Update shopfront deployment yaml file to use the configmap
  - After the image in the yaml file, add the below lines under spec.template.spec.containers
```yaml
        env:
          - name: PRODUCT_CATALOGUE_URL # environment variable name in application.properties of shopfront
            valueFrom: # value from configmap
              configMapKeyRef: # reference to configmap
                name: ecommerce-app-config # name of the configmap
                key: productcatalogue-url # key in the configmap
          - name: STOCK_MANAGER_URL # environment variable name in application.properties of shopfront
            valueFrom:
              configMapKeyRef:
                name: ecommerce-app-config
                key: stockmanager-url
```
- Connect to the EKS cluster using AWS CLI
```sh
$ aws eks --region eu-west-1 update-kubeconfig --name ecommerce-app
```
- Deploy the microservices to the Kubernetes cluster using kubectl in the following order:
  - ecommerce-app-config.yaml
  - productcatalogue-service.yaml
  - stockmanager-service.yaml
  - shopfront-service.yaml
```sh
$ kubectl apply -f ecommerce-app-config.yaml
$ kubectl apply -f productcatalogue-service.yaml
$ kubectl apply -f stockmanager-service.yaml
$ kubectl apply -f shopfront-service.yaml
```
- Get the external IP of the shopfront service using the below command
```sh
$ kubectl get svc
```
- Get the EXTERNAL-IP of the shopfront service, the url is like: xxx.eu-west-1.elb.amazonaws.com
- Access the shopfront service using the external IP in the browser
- You can also access to the productcatalogue and stockmanager services using the same method
  - For productcatalogue, the url is like: xxx.eu-west-1.elb.amazonaws.com/products
  - For stockmanager, the url is like: xxx.eu-west-1.elb.amazonaws.com/stocks

### Credit for java spring boot microservices: Daniel Bryant
https://github.com/danielbryantuk/oreilly-docker-java-shopping/