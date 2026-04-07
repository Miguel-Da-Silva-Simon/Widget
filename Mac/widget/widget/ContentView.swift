import SwiftUI
import WidgetSharedKit

struct ContentView: View {
    @ObservedObject var model: WidgetAppModel
    @State private var loginEmail = ""
    @State private var loginPassword = ""

    var body: some View {
        Group {
            if model.session.isAuthenticated {
                signedInView
            } else {
                loginView
            }
        }
        .onAppear { model.refresh() }
        .frame(width: 340)
        .fixedSize()
    }

    // MARK: - Login

    private var loginView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Fichaje")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color(red: 0.37, green: 0.59, blue: 0.98))

            Text("Inicia sesión con tus credenciales.")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            TextField("Email", text: $loginEmail)
                .textContentType(.username)
                .textFieldStyle(.roundedBorder)

            SecureField("Contraseña", text: $loginPassword)
                .textContentType(.password)
                .textFieldStyle(.roundedBorder)

            if let err = model.lastError {
                Text(err)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack {
                Spacer()
                Button("Entrar") {
                    model.signIn(email: loginEmail, password: loginPassword)
                }
                .keyboardShortcut(.defaultAction)
                .disabled(
                    model.authInProgress
                        || loginEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        || loginPassword.isEmpty
                )
            }

            if model.authInProgress {
                ProgressView()
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(24)
    }

    // MARK: - Signed in

    private var signedInView: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Fichaje")
                .font(.title2.weight(.bold))
                .foregroundStyle(Color(red: 0.37, green: 0.59, blue: 0.98))

            Text("Bienvenido, \(model.session.user?.displayName ?? "Usuario")")
                .font(.headline)

            if let err = model.lastError {
                Text(err)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 12) {
                Button("Cerrar sesión") { model.signOut() }
                Spacer()
                Button("Refrescar") { model.refresh() }
            }
        }
        .padding(24)
    }
}

#Preview {
    ContentView(model: WidgetAppModel())
}
