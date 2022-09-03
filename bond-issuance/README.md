# Bond Issuance

- Investor Terms of the Bond are settled off-ledge
- The Term State is issued on the ledger to represent the agreed upon Bond Terms on the ledger
```shell
  start CreateAndIssueTerm bondName: JTK, couponPaymentLeft: 100, interestRate: 5, purchasePrice: 250, \ 
    unitsAvailable: 100, maturityDate: 20230810, bondType: GB, currency: SGD, creditRating: AAA
```
- The Term State are made queryable to the UI to display to the investors
  - should have fields that are queryable and fields that are masked
```shell

start GetBondGreaterThanMaturityDate maturityDate: 20221201
start GetBondLessThanMaturityDate maturityDate: 20221201
start GetBondByRating creditRating: AA+
start GetBondByCurrency SGD

```
- request will generate a BondState which has a copy of the terms and is shared by the Issuer and Investor
- Once all the bonds are sold, the issuer can start giving coupon payments