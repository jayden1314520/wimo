<template>
  <Transition name="auth">
    <div v-if="authOpen" class="auth-mask" @click.self="close">
      <div class="auth-card" role="dialog" aria-modal="true" :aria-label="t('auth.brandTitle')">
        <button class="auth-card__close icon-btn" :aria-label="t('auth.close')" @click="close">
          <AppIcon name="close" :size="18" />
        </button>

        <!-- 协议 / 隐私政策 正文视图 -->
        <template v-if="legalView">
          <button class="auth-legal__back" @click="legalView = ''">
            <AppIcon name="chevron-down" :size="14" class="auth-legal__back-icon" />
            {{ t('auth.back') }}
          </button>
          <h2 class="auth-legal__title">{{ t(legalView === 'terms' ? 'auth.termsTitle' : 'auth.privacyTitle') }}</h2>
          <div class="auth-legal__body">
            <p v-for="(para, i) in legalParas" :key="i">{{ para }}</p>
          </div>
        </template>

        <template v-else>
          <div class="auth-card__head">
            <div class="auth-card__logo">
              <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
                <path d="M12 1.6l2.35 6.75 7.05 2.05-7.05 2.05L12 19.2l-2.35-6.75L2.6 10.4l7.05-2.05L12 1.6z" />
                <circle cx="19.4" cy="19.2" r="2.7" />
              </svg>
              <span>{{ t('brand.name') }}</span>
            </div>

            <!-- 登录 / 注册 Tab -->
            <div class="auth-tabs" role="tablist">
              <button
                type="button" role="tab" class="auth-tabs__item"
                :class="{ 'is-active': mode === 'login' }" :aria-selected="mode === 'login'"
                @click="switchMode('login')"
              >{{ t('auth.tabLogin') }}</button>
              <button
                type="button" role="tab" class="auth-tabs__item"
                :class="{ 'is-active': mode === 'register' }" :aria-selected="mode === 'register'"
                @click="switchMode('register')"
              >{{ t('auth.tabRegister') }}</button>
            </div>
            <h2 class="auth-card__title">{{ t(mode === 'login' ? 'auth.loginTitle' : 'auth.registerTitle') }}</h2>
            <p class="auth-card__sub">{{ t(mode === 'login' ? 'auth.loginSub' : 'auth.registerSub') }}</p>
          </div>

          <form class="auth-form" novalidate @submit.prevent="submit">
            <!-- 邮箱 -->
            <div class="auth-field" :class="{ 'has-error': errors.email }">
              <label class="auth-field__label" for="auth-email">{{ t('auth.email') }}</label>
              <div class="auth-input">
                <AppIcon name="mail" :size="16" class="auth-input__icon" />
                <input
                  id="auth-email" v-model.trim="form.email" type="email"
                  autocomplete="email" :placeholder="t('auth.emailPh')"
                  @input="clearError('email')"
                />
              </div>
              <span v-if="errors.email" class="auth-field__err">{{ errors.email }}</span>
            </div>

            <!-- 验证码：注册 或 登录且选择验证码登录时显示 -->
            <div v-if="showCode" class="auth-field" :class="{ 'has-error': errors.code }">
              <label class="auth-field__label" for="auth-code">{{ t('auth.code') }}</label>
              <div class="auth-input auth-input--code">
                <AppIcon name="shield" :size="16" class="auth-input__icon" />
                <input
                  id="auth-code" v-model.trim="form.code" type="text" inputmode="numeric"
                  maxlength="6" autocomplete="one-time-code" :placeholder="t('auth.codePh')"
                  @input="clearError('code')"
                />
                <button
                  type="button" class="auth-code-btn"
                  :disabled="countdown > 0 || sending" @click="sendCode"
                >
                  {{ countdown > 0 ? t('auth.resend', { n: countdown }) : t('auth.sendCode') }}
                </button>
              </div>
              <div v-if="demoCode" class="auth-demo">
                <AppIcon name="spark" :size="13" />
                {{ t('auth.demoTip') }} <b>{{ demoCode }}</b>
              </div>
              <span v-if="errors.code" class="auth-field__err">{{ errors.code }}</span>
            </div>

            <!-- 密码：注册 或 登录且选择密码登录时显示 -->
            <div v-if="showPassword" class="auth-field" :class="{ 'has-error': errors.password }">
              <div class="auth-field__row">
                <label class="auth-field__label" for="auth-pwd">{{ t('auth.password') }}</label>
                <button
                  v-if="mode === 'login'" type="button" class="auth-link auth-link--forgot"
                  @click="showToast(t('auth.forgotTip'))"
                >{{ t('auth.forgotPwd') }}</button>
              </div>
              <div class="auth-input">
                <AppIcon name="lock" :size="16" class="auth-input__icon" />
                <input
                  id="auth-pwd" v-model="form.password"
                  :type="showPwd ? 'text' : 'password'"
                  :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
                  :placeholder="t('auth.passwordPh')"
                  @input="clearError('password')"
                />
                <button type="button" class="auth-input__eye" :aria-label="t('auth.togglePwd')" @click="showPwd = !showPwd">
                  <AppIcon :name="showPwd ? 'eye-off' : 'eye'" :size="16" />
                </button>
              </div>
              <span v-if="errors.password" class="auth-field__err">{{ errors.password }}</span>
            </div>

            <!-- 确认密码：仅注册 -->
            <div v-if="mode === 'register'" class="auth-field" :class="{ 'has-error': errors.confirm }">
              <label class="auth-field__label" for="auth-pwd2">{{ t('auth.confirm') }}</label>
              <div class="auth-input">
                <AppIcon name="lock" :size="16" class="auth-input__icon" />
                <input
                  id="auth-pwd2" v-model="form.confirm" :type="showPwd ? 'text' : 'password'"
                  autocomplete="new-password" :placeholder="t('auth.confirmPh')"
                  @input="clearError('confirm')"
                />
              </div>
              <span v-if="errors.confirm" class="auth-field__err">{{ errors.confirm }}</span>
            </div>

            <!-- 登录方式切换（仅登录） -->
            <div v-if="mode === 'login'" class="auth-way">
              <button type="button" class="auth-link" @click="toggleLoginWay">
                {{ loginWay === 'password' ? t('auth.switchToCode') : t('auth.switchToPwd') }}
              </button>
            </div>

            <!-- 用户协议 / 隐私政策（仅注册） -->
            <div v-if="mode === 'register'" class="auth-agree" :class="{ 'has-error': errors.agree }">
              <label class="auth-agree__box">
                <input v-model="form.agree" type="checkbox" @change="clearError('agree')" />
                <span class="auth-agree__check"><AppIcon name="check" :size="12" /></span>
                <span class="auth-agree__text">
                  {{ t('auth.agreePrefix') }}
                  <a href="#" @click.prevent="legalView = 'terms'">{{ t('auth.terms') }}</a>
                  {{ t('auth.agreeAnd') }}
                  <a href="#" @click.prevent="legalView = 'privacy'">{{ t('auth.privacy') }}</a>
                </span>
              </label>
              <span v-if="errors.agree" class="auth-field__err">{{ errors.agree }}</span>
            </div>

            <button type="submit" class="auth-submit" :disabled="submitting">
              {{ submitting ? t('auth.submitting') : t(mode === 'login' ? 'auth.loginSubmit' : 'auth.registerSubmit') }}
            </button>

            <!-- 底部模式切换 -->
            <p class="auth-foot">
              {{ t(mode === 'login' ? 'auth.noAccount' : 'auth.hasAccount') }}
              <button type="button" class="auth-link" @click="switchMode(mode === 'login' ? 'register' : 'login')">
                {{ t(mode === 'login' ? 'auth.toRegister' : 'auth.toLogin') }}
              </button>
            </p>
          </form>
        </template>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
const { t } = useI18n()
const { authOpen, closeAuth, signIn, showToast, registerAccount, verifyPassword, hasAccount } = useAuth()

type Mode = 'login' | 'register'
type LoginWay = 'password' | 'code'

const mode = ref<Mode>('login')
const loginWay = ref<LoginWay>('password')

const form = reactive({ email: '', code: '', password: '', confirm: '', agree: false })
const errors = reactive<Record<string, string>>({})
const showPwd = ref(false)
const sending = ref(false)
const submitting = ref(false)
const countdown = ref(0)
const demoCode = ref('')
const legalView = ref<'' | 'terms' | 'privacy'>('')

let tickTimer: ReturnType<typeof setInterval> | null = null

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/* 字段显隐：注册＝验证码+密码+确认密码；登录按所选方式二选一 */
const showCode = computed(() => mode.value === 'register' || (mode.value === 'login' && loginWay.value === 'code'))
const showPassword = computed(() => mode.value === 'register' || (mode.value === 'login' && loginWay.value === 'password'))

const legalParas = computed(() => {
  const raw = t(legalView.value === 'terms' ? 'auth.termsBody' : 'auth.privacyBody')
  return String(raw).split('\n').filter(Boolean)
})

function clearError(key: string) { delete errors[key] }
function clearAllErrors() { Object.keys(errors).forEach(k => delete errors[k]) }

function stopCountdown() {
  if (tickTimer) { clearInterval(tickTimer); tickTimer = null }
  countdown.value = 0
  demoCode.value = ''
}

/** 切换登录/注册；保留已填邮箱，其余字段与错误清空 */
function switchMode(m: Mode) {
  if (mode.value === m) return
  mode.value = m
  form.code = ''; form.password = ''; form.confirm = ''; form.agree = false
  legalView.value = ''
  showPwd.value = false
  loginWay.value = 'password'
  clearAllErrors()
  stopCountdown()
}

function toggleLoginWay() {
  loginWay.value = loginWay.value === 'password' ? 'code' : 'password'
  form.code = ''; form.password = ''
  clearAllErrors()
  stopCountdown()
}

function close() {
  closeAuth()
  reset()
}

function reset() {
  form.email = ''; form.code = ''; form.password = ''; form.confirm = ''; form.agree = false
  clearAllErrors()
  stopCountdown()
  legalView.value = ''
  showPwd.value = false
  mode.value = 'login'
  loginWay.value = 'password'
}

/** 发送验证码：静态演示环境不接邮件服务，生成 6 位演示码并倒计时 */
function sendCode() {
  clearError('email'); clearError('code')
  if (!EMAIL_RE.test(form.email)) {
    errors.email = t('auth.errEmailInvalid')
    return
  }
  sending.value = true
  setTimeout(() => {
    sending.value = false
    demoCode.value = String(Math.floor(100000 + Math.random() * 900000))
    countdown.value = 60
    showToast(t('auth.codeSent'))
    if (tickTimer) clearInterval(tickTimer)
    tickTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && tickTimer) { clearInterval(tickTimer); tickTimer = null }
    }, 1000)
  }, 400)
}

function checkCode(): boolean {
  if (!form.code) { errors.code = t('auth.errCodeEmpty'); return false }
  if (!demoCode.value) { errors.code = t('auth.errCodeSend'); return false }
  if (form.code !== demoCode.value) { errors.code = t('auth.errCodeWrong'); return false }
  return true
}

function validate(): boolean {
  clearAllErrors()
  if (!form.email) errors.email = t('auth.errEmailEmpty')
  else if (!EMAIL_RE.test(form.email)) errors.email = t('auth.errEmailInvalid')

  if (mode.value === 'register') {
    checkCode()
    if (!form.password) errors.password = t('auth.errPwdEmpty')
    else if (form.password.length < 6) errors.password = t('auth.errPwdShort')
    if (!form.confirm) errors.confirm = t('auth.errConfirmEmpty')
    else if (form.confirm !== form.password) errors.confirm = t('auth.errConfirmMismatch')
    if (!form.agree) errors.agree = t('auth.errAgree')
  } else if (loginWay.value === 'password') {
    if (!form.password) errors.password = t('auth.errPwdEmpty')
  } else {
    checkCode()
  }
  return Object.keys(errors).length === 0
}

function submit() {
  if (!validate()) return
  submitting.value = true
  setTimeout(() => {
    submitting.value = false
    const email = form.email

    if (mode.value === 'register') {
      const ok = registerAccount(email, form.password)
      if (!ok) { errors.email = t('auth.errEmailTaken'); return }
      signIn(email)
      closeAuth()
      showToast(t('auth.registerSuccess'))
      reset()
      return
    }

    // 登录：先确认账号已注册；密码登录再校验密码
    if (!hasAccount(email)) { errors.email = t('auth.errNotRegistered'); return }
    if (loginWay.value === 'password') {
      if (verifyPassword(email, form.password) === 'wrong') {
        errors.password = t('auth.errWrongPwd')
        return
      }
    }
    signIn(email)
    closeAuth()
    showToast(t('auth.loginSuccess'))
    reset()
  }, 600)
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && authOpen.value) {
    if (legalView.value) legalView.value = ''
    else close()
  }
}
onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  if (tickTimer) clearInterval(tickTimer)
})
</script>
