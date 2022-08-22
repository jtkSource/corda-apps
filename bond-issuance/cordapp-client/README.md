# Corda web app

## Starting  the webserver

To run the web server to connect to any of the corda nodes - `gs|hsbc|citi|mas|sgx|cb`


```shell
./script/server.sh <gs|hsbc|citi|mas|sgx|cb>
```

Bank nodes are:
- gs: Goldman Sachs
- hsbc: HSBC
- cit: Citi

  Banks can issue Bond Terms, Request for bond Terms, transfer cash and perform coupon payments

Observer node:

- mas

  Observers essentially will be able to see all reportable transaction on the platform 

Notary node:

- sgx

  Notary are responsible for validating the transactions between various parties

Central Bank node

- cb

  Responsible for issuing new digital currencies and transferring the non-fungabile tokens to various Banks

## API

### Node API

Fetch various information on the nodes the webserver is connected to
- GET http://{{host}}:{{port}}/corda/addresses
- GET http://{{host}}:{{port}}/corda/identities
- GET http://{{host}}:{{port}}/corda/platformversion
- GET http://{{host}}:{{port}}/corda/peers
- GET http://{{host}}:{{port}}/corda/notaries
- GET http://{{host}}:{{port}}/corda/flows
- GET http://{{host}}:{{port}}/corda/me
- GET http://{{host}}:{{port}}/corda/states

### Bond Term API

- Create Term issue
```http request
POST http://{{host}}:{{port}}/corda/terms/issue
Content-Type: application/json

{
"bondName": "GS-30Y-TB-USD",
"interestRate": 12.0,
"parValue": 1000,
"unitsAvailable": 500,
"maturityDate": "20320810",
"bondType": "TB",
"currency": "USD",
"creditRating": "AAA",
"paymentFrequencyInMonths": 6
}
```

- Query Terms

  - byCurrency
  - byRating
  - lessThanMaturityDate
  - greaterThanMaturityDate
  - byTermStateLinearID
```http request
POST http://{{host}}:{{port}}/corda/terms/query
Content-Type: application/json

{
  "queryType": "greaterThanMaturityDate",
  "queryValue": "20220812"
}

```

### Bond API

- **Issue bond**

Creates n bond from the specified termId. The request cannot be fired from the same node that create the term  
```http request

POST http://{{host}}:{{port}}/corda/bonds/issue
Content-Type: application/json

{
  "teamStateLinearID": "687dca98-2463-4900-b0e8-f9c55ad34b35",
  "unitsOfBonds": 10
}
```

- **Query Bond**

  - byCurrency
  - byRating
  - lessThanMaturityDate
  - greaterThanMaturityDate
  - byTermStateLinearID
```http request

POST http://{{host}}:{{port}}/corda/bonds/query
Content-Type: application/json

{
  "queryType": "greaterThanMaturityDate",
  "queryValue": "20220812"
}
```

- **Total Bond Tokens Issued**

Returns the total bond tokens created for a particular `termId` 

```http request
GET http://{{host}}:{{port}}/corda/bonds/tokens/{{termId}}
Content-Type: application/json
```

#### Bond Coupon API

- (TBD) Start the coupon schedule on the node
```http request
POST http://{{host}}:{{port}}/corda/bond/coupon/schedule
Content-Type: application/json

{
  "schedulePeriodInSeconds": 30
}
```

- Manually pay coupon

```http request

```
### Cash API

- **Issue cash**

> This API can only be used by Central Bank (cb) node.
> 
This API is used to create digital currency on the corda network

```http request
POST http://{{host}}:{{port}}/corda/cash/issue
Content-Type: application/json

{
  "amount": "10000000000",
  "currencyCode": "USD",
  "usdRate": 1.0
}

```

- **Query Cash**

This API can be called by any node and is used to query the cash states currently on the network
```http request
POST http://{{host}}:{{port}}/corda/cash/query
Content-Type: application/json

{
  "queryType": "byCurrencyCode",
  "queryValue": "USD"
}
```

- **Transfer Cash**

This API allows transferring cash between Central Banks and Banks and also between banks

```http request
POST http://{{host}}:{{port}}/corda/cash/transfer
Content-Type: application/json

{
  "amount": "15000000",
  "currencyCode": "USD",
  "recipient": "Goldman Sachs"
}

```


```json
[{
  "GS": {
    "total": "15230000" //14780000
  }
},
{
  "HSBC":  {
  "total": "2825000.0" //3275000.0
  }
},
{
"CITI": {
  "total": "1945000.0" //1945000
  }
}]
```