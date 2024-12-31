package com.looker.kenko.ui.addEditExercise

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.looker.kenko.R
import com.looker.kenko.data.StringHandler
import com.looker.kenko.data.model.Exercise
import com.looker.kenko.data.model.MuscleGroups
import com.looker.kenko.data.repository.ExerciseRepo
import com.looker.kenko.ui.addEditExercise.navigation.AddEditExerciseRoute
import com.looker.kenko.utils.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditExerciseViewModel @Inject constructor(
    private val repo: ExerciseRepo,
    private val stringHandler: StringHandler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeData = savedStateHandle.toRoute<AddEditExerciseRoute>()

    private val exerciseId: Int? = routeData.id

    private val defaultTarget: MuscleGroups? = routeData.target?.let { MuscleGroups.valueOf(it) }

    private val targetMuscle = MutableStateFlow(MuscleGroups.Chest)

    private val isIsometric = MutableStateFlow(false)

    private val isReadOnly: Boolean = exerciseId != null

    val snackbarState = SnackbarHostState()

    var exerciseName: String by mutableStateOf("")
        private set

    var reference: String by mutableStateOf("")
        private set

    private val isReferenceInvalid: Flow<Boolean> =
        snapshotFlow { reference }
            .mapLatest { it.toHttpUrlOrNull() == null && it.isNotBlank() }

    private val exerciseAlreadyExistError: Flow<Boolean> =
        snapshotFlow { exerciseName }
            .mapLatest { repo.isExerciseAvailable(it) && !isReadOnly }

    val state = combine(
        targetMuscle,
        isIsometric,
        flowOf(isReadOnly),
        exerciseAlreadyExistError,
        isReferenceInvalid,
    ) { target, isometric, readOnly, alreadyExist, referenceInvalid ->
        AddEditExerciseUiState(
            targetMuscle = target,
            isIsometric = isometric,
            isReadOnly = readOnly,
            isError = alreadyExist,
            isReferenceInvalid = referenceInvalid,
        )
    }.asStateFlow(
        AddEditExerciseUiState(
            targetMuscle = MuscleGroups.Chest,
            isIsometric = false,
            isError = false,
            isReadOnly = false,
            isReferenceInvalid = false,
        )
    )

    fun setName(value: String) {
        exerciseName = value
    }

    fun addReference(value: String) {
        reference = value
    }

    fun setTargetMuscle(value: MuscleGroups) {
        viewModelScope.launch {
            targetMuscle.emit(value)
        }
    }

    fun setIsometric(value: Boolean) {
        viewModelScope.launch {
            isIsometric.emit(value)
        }
    }

    fun addNewExercise(onDone: () -> Unit) {
        viewModelScope.launch {
            if (exerciseName.isBlank()) {
                snackbarState.showSnackbar(stringHandler.getString(R.string.error_exercise_name_empty))
                return@launch
            }
            if (state.value.isReferenceInvalid) {
                snackbarState.showSnackbar(stringHandler.getString(R.string.error_invalid_reference_format))
                return@launch
            }
            repo.upsert(
                Exercise(
                    name = exerciseName,
                    target = targetMuscle.value,
                    reference = reference.ifBlank { null },
                    isIsometric = isIsometric.value,
                    id = exerciseId,
                )
            )
            onDone()
        }
    }

    init {
        viewModelScope.launch {
            if (exerciseId != null) {
                val exercise = repo.get(exerciseId)
                exercise?.let {
                    setName(it.name)
                    addReference(it.reference ?: "")
                    setIsometric(it.isIsometric)
                    setTargetMuscle(it.target)
                }
            } else if (defaultTarget != null) {
                setTargetMuscle(defaultTarget)
            }
        }
    }
}

data class AddEditExerciseUiState(
    val targetMuscle: MuscleGroups,
    val isIsometric: Boolean,
    val isError: Boolean,
    val isReadOnly: Boolean,
    val isReferenceInvalid: Boolean,
)
