import SwiftUI

struct MockLoginSheet: View {
    @Binding var displayName: String
    var onSignIn: () -> Void
    var onCancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Iniciar sesión (mock)")
                .font(.headline)
            Text("Mismo flujo que en Windows: el widget no autentica solo; acá simulamos la app acompañante.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            TextField("Nombre para mostrar (opcional)", text: $displayName)
                .textFieldStyle(.roundedBorder)
            HStack {
                Button("Cancelar", role: .cancel) { onCancel() }
                Spacer()
                Button("Entrar") { onSignIn() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(24)
        .frame(minWidth: 360)
    }
}
