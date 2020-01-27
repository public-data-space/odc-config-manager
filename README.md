# IDS Open Data Connector Config Manager

This repository contais the logic of the connectors internal configurations, namely management of the apps.

The main repository of the IDS Open Data Connector is: 

ids-open-data-connector

## Requirements
* docker
* docker-compose 3.5 or higher
* maven

## Building the Component
* run ``mvn clean package``

## Using the Component
Use the docker-compose_build.yml file in the *ids-open-data-connector* repository to boot the component along with the 
other connector components.

