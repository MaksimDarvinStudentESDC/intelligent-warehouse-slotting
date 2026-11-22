export type Batch={id:number;batchCode:string;quantity:number;volumeDm3:number;weightKg:number;receivingDate:string;expiryDate:string|null;status:string};
export type Location={id:number;code:string;zoneId:number;capacityDm3:number;occupiedDm3:number;maxWeightKg:number;distanceToReceivingM:number;distanceToPickingM:number;distanceToShippingM:number;active:boolean};
export type Recommendation={rank:number;locationId:number;locationCode:string;distanceMeters:number;explanation:string};
export type RecommendationResult={batchId:number;strategy:string;feasibleCount:number;recommendations:Recommendation[]};
export type CreateBatch={productId:number;batchCode:string;quantity:number;receivingDate:string;expiryDate:string|null};
