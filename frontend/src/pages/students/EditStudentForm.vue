<template>
  <el-form
    ref="studentForm"
    :model="studentBeingEdited"
    :rules="rules"
    label-width="110px"
    style="padding-right: 30px"
    label-position="left"
  >
    <el-form-item label="Id:">
      <el-input v-model="studentBeingEdited.id" disabled></el-input>
    </el-form-item>
    <el-form-item label="Username:" prop="username">
      <el-input v-model="studentBeingEdited.username"></el-input>
    </el-form-item>
    <el-form-item label="Status:" prop="enabled">
      <!-- An active student is deactivated only through the Deactivate button (UC-STU-deactivate-student) -->
      <template v-if="isActive">
        <el-tag type="success">Enabled</el-tag>
        <el-button
          type="danger"
          plain
          size="small"
          style="margin-left: 12px"
          :loading="deactivating"
          @click="deactivateExistingStudent()"
        >
          Deactivate
        </el-button>
      </template>
      <!-- Extension 3a1: a deactivated student is offered reactivation instead (interim path until UC-STU-reactivate-student) -->
      <el-switch
        v-else
        v-model="studentBeingEdited.enabled"
        inline-prompt
        active-text="Enabled"
        inactive-text="Disabled"
        style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
      />
    </el-form-item>
    <el-form-item label="First Name:" prop="firstName">
      <el-input v-model="studentBeingEdited.firstName"></el-input>
    </el-form-item>
    <el-form-item label="Last Name:" prop="lastName">
      <el-input v-model="studentBeingEdited.lastName"></el-input>
    </el-form-item>
    <el-form-item label="Email:" prop="email">
      <el-input v-model="studentBeingEdited.email"></el-input>
    </el-form-item>
    <el-form-item label="Team:" prop="teamName">
      <el-input v-model="studentBeingEdited.teamName" disabled></el-input>
    </el-form-item>
    <div style="display: flex; justify-content: end">
      <el-button @click="$emit('close-dialog')"> Cancel </el-button>
      <el-button type="primary" @click="updateExistingStudent()"> Confirm </el-button>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Student } from '@/apis/student/types'
import type { FormInstance } from 'element-plus'
import { deactivateStudent, findStudentById, updateStudent } from '@/apis/student'
import { h, ref, onMounted } from 'vue'

const { studentId } = defineProps(['studentId'])
const emit = defineEmits(['close-dialog'])

// Note that the structure of the userData is NOT the same as the User interface since the roles are stored as an array
const studentBeingEdited = ref<Student>({
  // Data of the user being added or edited
  id: NaN,
  username: '',
  enabled: false,
  firstName: '',
  lastName: '',
  email: ''
})

// Whether the student was active when loaded; decides between the Deactivate button and the reactivation switch
const isActive = ref<boolean>(false)
const deactivating = ref<boolean>(false)

const studentForm = ref<FormInstance>() // Form reference

// Validation rules
const rules = {
  username: [
    { required: true, message: 'Please input your email as your username', trigger: 'blur' },
    {
      pattern: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
      message: 'Enter your email as username.',
      trigger: 'blur'
    }
  ],
  enabled: [{ required: true, message: 'Please select the status of the user.', trigger: 'blur' }],
  firstName: [
    { required: true, message: 'Please provide the first name of the user.', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Please provide the last name of the user.', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Please provide the email of the user.', trigger: 'blur' },
    { type: 'email', message: 'Please provide a valid email.', trigger: 'blur' }
  ]
}

async function loadStudent(studentId: number) {
  // Load the user to be edited
  const result = await findStudentById(studentId)

  studentBeingEdited.value.id = result.data.id as number
  studentBeingEdited.value.username = result.data.username
  studentBeingEdited.value.enabled = result.data.enabled
  studentBeingEdited.value.firstName = result.data.firstName
  studentBeingEdited.value.lastName = result.data.lastName
  studentBeingEdited.value.email = result.data.email
  studentBeingEdited.value.teamName = result.data.teamName
  isActive.value = result.data.enabled === true
}

onMounted(() => {
  loadStudent(studentId) // Load the artifact to be edited
})

async function updateExistingStudent() {
  await studentForm.value!.validate() // Validate the form

  const updatedStudent: Student = {
    id: studentBeingEdited.value.id,
    username: studentBeingEdited.value.username,
    // An active student stays active here: deactivation has its own confirmed action
    enabled: isActive.value ? true : studentBeingEdited.value.enabled,
    firstName: studentBeingEdited.value.firstName,
    lastName: studentBeingEdited.value.lastName,
    email: studentBeingEdited.value.email
  }
  console.log(updatedStudent)
  // Call the API to update the user
  await updateStudent(updatedStudent)

  ElMessage.success('Student updated successfully')
  emit('close-dialog')
}

// UC-STU-deactivate-student, steps 3-6: warn with every consequence, ask for confirmation, then deactivate
async function deactivateExistingStudent() {
  const name = `${studentBeingEdited.value.firstName} ${studentBeingEdited.value.lastName}`
  const consequences = [
    'They will no longer be able to log in or submit weekly activity reports and peer evaluations. If they are logged in now, they are signed out on their next action.',
    'Their account and previously submitted weekly activity reports and peer evaluations are kept.',
    'They stay on their team. Teammates no longer owe, and can no longer submit, peer evaluations of them, and reminders to them stop.',
    'They can be reactivated later. This is not deletion.'
  ]

  try {
    await ElMessageBox.confirm(
      h('div', [
        h('p', `Deactivate ${name}?`),
        h(
          'ul',
          { style: 'padding-left: 18px; margin: 8px 0 0' },
          consequences.map((c) => h('li', c))
        )
      ]),
      'Deactivate student',
      {
        confirmButtonText: 'Deactivate',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )
  } catch {
    // Extension 4a: canceled at the warning; no request is sent and the student stays active
    ElMessage.info('Deactivation canceled')
    return
  }

  deactivating.value = true
  try {
    await deactivateStudent(studentBeingEdited.value.id as number)
    ElMessage.success(`${name} has been deactivated.`)
    emit('close-dialog')
  } finally {
    deactivating.value = false
  }
}
</script>

<style scoped lang="scss"></style>
