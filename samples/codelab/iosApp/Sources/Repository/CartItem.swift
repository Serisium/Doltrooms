//
//  CartItem.swift
//  Fruitties
//
//  Created by Ben Trengrove on 28/3/2025.
//  Copyright © 2025 orgName. All rights reserved.
//

import sharedKit

struct CartItem: Hashable {
   let entity: CartItemWithFruittie

   let fruittie: Fruittie?

   var id: Int64 {
       entity.cartItem.id
   }

   var count: Int64 {
       Int64(entity.cartItem.count)
   }

   init(entity: CartItemWithFruittie) {
       self.entity = entity
       self.fruittie = Fruittie(entity: entity.fruittie)
   }
}
