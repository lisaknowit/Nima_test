# Application
This application is build as a IaC (Infrastructure as Code). The majority of the stack can be build with SAM (Serverless Application model) template which is used to build the deploy. The architecture is serverless and are build with lambda functions. 

<b> N.B!</b> In this application both QA and PROD versions belongs to the same stack. Meaning the code includes environment variables for both QA and PROD so when changing code. Note that you are changing correctly. QA-specific variables have the prefix ```QA_``` and methods have the prefix at te end. The PROD related values have not any prefix.

## AWS CLI
AWS Command Line Interface can be used to run aws commands directly from terminal. But be careful when using it!
Download it here: https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html

## Webhook subscription setup
Switch (Eon) have IP address ranges that are NOT ALLOWED, therefore, a custom domain have to be created and used for API gateway. 

### Custom Domain
A custom domain has been created manually in the API Gateway Console. </br>
To access it from Postman use adress: ```<custom-domain-name>/powerlimit ```

## Configuration
The configuration has to be placed in AWS parameter store with the name "config" and has to be in YAML-format e.g.:</br>
- siteId: 10</br>
  name: Malmö Bulltofta</br>
  groupId: 42942</br>
  openAdrId_QA: 123</br>
  openAdrId_PROD: xxx</br>
  defaultMaxGridPower: 412371</br>
  maxGridPowerLimit: 1000</br>
  siteLimit: 0</br>
  connector: Driivz1</br>
- siteId: 11</br>
  name: Malmö Toftanäs</br>
  groupId: 43106</br>
  openAdrId_QA: 131</br>
  openAdrId_PROD: xxx</br>
  defaultMaxGridPower: 309278</br>
  maxGridPowerLimit: 1000</br>
  siteLimit: 0</br>
  connector: Driivz1</br>
