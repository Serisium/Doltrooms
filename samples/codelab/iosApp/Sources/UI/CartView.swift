import SwiftUI

struct CartView : View {
    @State
    private var expanded = false

    @ObservedObject
    private(set) var uiModel: ContentViewModel

    var body: some View {
        if (uiModel.cartItems.isEmpty) {
            Text("Cart is empty, add some items").padding()
        } else {
            HStack {
                Text("Cart has \(uiModel.cartItems.reduce(0){$0 + $1.count}) items of \(uiModel.cartItems.count) types of fruit")
                    .padding()

                Spacer()

                Button {
                    expanded.toggle()
                } label: {
                    if (expanded) {
                        Text("collapse")
                    } else {
                        Text("expand")
                    }
                }
                .padding()
            }
            if (expanded) {
                VStack {
                    ForEach(uiModel.cartItems, id: \.self) { item in
                        Text("\(item.fruittie!.name!): \(item.count)")
                    }
                }
            }
        }
    }
}
