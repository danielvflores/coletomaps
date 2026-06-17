package com.example.coletomaps.ui.login

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.coletomaps.R
import com.example.coletomaps.ui.data.FirebaseManager
import com.example.coletomaps.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Acción para Iniciar Sesión
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validarCampos(email, password)) {
                FirebaseManager.auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Si es correcto, navegamos al Mapa (HU02)
                            findNavController().navigate(R.id.nav_map)
                        } else {
                            Toast.makeText(requireContext(), "Error: Contraseña incorrecta o usuario no existe.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        // Acción para Registrar un nuevo usuario (HU01)
        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (validarCampos(email, password)) {
                FirebaseManager.auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(requireContext(), "¡Cuenta creada con éxito!", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.nav_map)
                        } else {
                            Toast.makeText(requireContext(), "Error al registrar: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    // Cumpliendo pruebas de aceptación de formato y vacíos
    private fun validarCampos(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.etEmail.error = "El correo no puede estar vacío"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Formato de correo inválido"
            return false
        }
        if (password.isEmpty() || password.length < 6) {
            binding.etPassword.error = "La contraseña debe tener al menos 6 caracteres"
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}