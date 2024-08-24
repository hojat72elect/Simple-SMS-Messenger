package com.simplemobiletools.smsmessenger.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import com.google.gson.Gson
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.smsmessenger.dialogs.RadioGroupDialog
import com.simplemobiletools.smsmessenger.helpers.MyContactsContentProvider
import com.simplemobiletools.smsmessenger.helpers.NavigationIcon
import com.simplemobiletools.smsmessenger.helpers.PERMISSION_READ_CONTACTS
import com.simplemobiletools.smsmessenger.helpers.SimpleContactsHelper
import com.simplemobiletools.smsmessenger.helpers.ensureBackgroundThread
import com.simplemobiletools.smsmessenger.models.RadioItem
import com.simplemobiletools.smsmessenger.models.SimpleContact
import com.simplemobiletools.smsmessenger.R
import com.simplemobiletools.smsmessenger.adapters.ContactsAdapter
import com.simplemobiletools.smsmessenger.databinding.ActivityNewConversationBinding
import com.simplemobiletools.smsmessenger.databinding.ItemSuggestedContactBinding
import com.simplemobiletools.smsmessenger.extensions.applyColorFilter
import com.simplemobiletools.smsmessenger.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.smsmessenger.extensions.beGone
import com.simplemobiletools.smsmessenger.extensions.beVisible
import com.simplemobiletools.smsmessenger.extensions.beVisibleIf
import com.simplemobiletools.smsmessenger.extensions.getColorStateList
import com.simplemobiletools.smsmessenger.extensions.getContrastColor
import com.simplemobiletools.smsmessenger.extensions.getMyContactsCursor
import com.simplemobiletools.smsmessenger.extensions.getPhoneNumberTypeText
import com.simplemobiletools.smsmessenger.extensions.getProperPrimaryColor
import com.simplemobiletools.smsmessenger.extensions.getProperTextColor
import com.simplemobiletools.smsmessenger.extensions.getSuggestedContacts
import com.simplemobiletools.smsmessenger.extensions.getThreadId
import com.simplemobiletools.smsmessenger.extensions.hasPermission
import com.simplemobiletools.smsmessenger.extensions.hideKeyboard
import com.simplemobiletools.smsmessenger.extensions.normalizeString
import com.simplemobiletools.smsmessenger.extensions.onTextChangeListener
import com.simplemobiletools.smsmessenger.extensions.toast
import com.simplemobiletools.smsmessenger.extensions.underlineText
import com.simplemobiletools.smsmessenger.extensions.updateTextColors
import com.simplemobiletools.smsmessenger.extensions.value
import com.simplemobiletools.smsmessenger.extensions.viewBinding
import com.simplemobiletools.smsmessenger.helpers.THREAD_ATTACHMENT_URI
import com.simplemobiletools.smsmessenger.helpers.THREAD_ATTACHMENT_URIS
import com.simplemobiletools.smsmessenger.helpers.THREAD_ID
import com.simplemobiletools.smsmessenger.helpers.THREAD_NUMBER
import com.simplemobiletools.smsmessenger.helpers.THREAD_TEXT
import com.simplemobiletools.smsmessenger.helpers.THREAD_TITLE
import com.simplemobiletools.smsmessenger.messaging.isShortCodeWithLetters
import java.net.URLDecoder
import java.util.Locale

class NewConversationActivity : SimpleActivity() {
    private var allContacts = ArrayList<SimpleContact>()
    private var privateContacts = ArrayList<SimpleContact>()

    private val binding by viewBinding(ActivityNewConversationBinding::inflate)

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.new_conversation)
        updateTextColors(binding.newConversationHolder)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.newConversationCoordinator,
            nestedView = binding.contactsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(
            scrollingView = binding.contactsList,
            toolbar = binding.newConversationToolbar
        )

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        binding.newConversationAddress.requestFocus()

        // READ_CONTACTS permission is not mandatory, but without it we won't be able to show any suggestions during typing
        handlePermission(PERMISSION_READ_CONTACTS) {
            initContacts()
        }
    }

    @SuppressLint("NewApi")
    override fun onResume() {
        super.onResume()
        setupToolbar(binding.newConversationToolbar, NavigationIcon.Arrow)
        binding.noContactsPlaceholder2.setTextColor(getProperPrimaryColor())
        binding.noContactsPlaceholder2.underlineText()
        binding.suggestionsLabel.setTextColor(getProperPrimaryColor())
    }

    @SuppressLint("NewApi")
    private fun initContacts() {
        if (isThirdPartyIntent()) {
            return
        }

        fetchContacts()
        binding.newConversationAddress.onTextChangeListener { searchString ->
            val filteredContacts = ArrayList<SimpleContact>()
            allContacts.forEach { contact ->
                if (contact.phoneNumbers.any { it.normalizedNumber.contains(searchString, true) } ||
                    contact.name.contains(searchString, true) ||
                    contact.name.contains(searchString.normalizeString(), true) ||
                    contact.name.normalizeString().contains(searchString, true)) {
                    filteredContacts.add(contact)
                }
            }

            filteredContacts.sortWith(compareBy { !it.name.startsWith(searchString, true) })
            setupAdapter(filteredContacts)

            binding.newConversationConfirm.beVisibleIf(searchString.length > 2)
        }

        binding.newConversationConfirm.applyColorFilter(getProperTextColor())
        binding.newConversationConfirm.setOnClickListener {
            val number = binding.newConversationAddress.value
            if (isShortCodeWithLetters(number)) {
                binding.newConversationAddress.setText("")
                toast(R.string.invalid_short_code, length = Toast.LENGTH_LONG)
                return@setOnClickListener
            }
            launchThreadActivity(number, number)
        }

        binding.noContactsPlaceholder2.setOnClickListener {
            handlePermission(PERMISSION_READ_CONTACTS) {
                if (it) {
                    fetchContacts()
                }
            }
        }

        val properPrimaryColor = getProperPrimaryColor()
        binding.contactsLetterFastscroller.textColor = getProperTextColor().getColorStateList()
        binding.contactsLetterFastscroller.pressedTextColor = properPrimaryColor
        binding.contactsLetterFastscrollerThumb.setupWithFastScroller(binding.contactsLetterFastscroller)
        binding.contactsLetterFastscrollerThumb.textColor = properPrimaryColor.getContrastColor()
        binding.contactsLetterFastscrollerThumb.thumbColor = properPrimaryColor.getColorStateList()
    }

    private fun isThirdPartyIntent(): Boolean {
        if ((intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW) && intent.dataString != null) {
            val number =
                intent.dataString!!.removePrefix("sms:").removePrefix("smsto:").removePrefix("mms")
                    .removePrefix("mmsto:").replace("+", "%2b").trim()
            launchThreadActivity(URLDecoder.decode(number), "")
            finish()
            return true
        }
        return false
    }

    private fun fetchContacts() {
        fillSuggestedContacts {
            SimpleContactsHelper(this).getAvailableContacts(false) {
                allContacts = it

                if (privateContacts.isNotEmpty()) {
                    allContacts.addAll(privateContacts)
                    allContacts.sort()
                }

                runOnUiThread {
                    setupAdapter(allContacts)
                }
            }
        }
    }

    @SuppressLint("NewApi")
    private fun setupAdapter(contacts: ArrayList<SimpleContact>) {
        val hasContacts = contacts.isNotEmpty()
        binding.contactsList.beVisibleIf(hasContacts)
        binding.noContactsPlaceholder.beVisibleIf(!hasContacts)
        binding.noContactsPlaceholder2.beVisibleIf(
            !hasContacts && !hasPermission(
                PERMISSION_READ_CONTACTS
            )
        )

        if (!hasContacts) {
            val placeholderText = if (hasPermission(PERMISSION_READ_CONTACTS)) {
                R.string.no_contacts_found
            } else {
                R.string.no_access_to_contacts
            }

            binding.noContactsPlaceholder.text = getString(placeholderText)
        }

        val currAdapter = binding.contactsList.adapter
        if (currAdapter == null) {
            ContactsAdapter(this, contacts, binding.contactsList) {
                hideKeyboard()
                val contact = it as SimpleContact
                val phoneNumbers = contact.phoneNumbers
                if (phoneNumbers.size > 1) {
                    val primaryNumber = contact.phoneNumbers.find { it.isPrimary }
                    if (primaryNumber != null) {
                        launchThreadActivity(primaryNumber.value, contact.name)
                    } else {
                        val items = ArrayList<RadioItem>()
                        phoneNumbers.forEachIndexed { index, phoneNumber ->
                            val type = getPhoneNumberTypeText(phoneNumber.type, phoneNumber.label)
                            items.add(
                                RadioItem(
                                    index,
                                    "${phoneNumber.normalizedNumber} ($type)",
                                    phoneNumber.normalizedNumber
                                )
                            )
                        }

                        RadioGroupDialog(this, items) {
                            launchThreadActivity(it as String, contact.name)
                        }
                    }
                } else {
                    launchThreadActivity(phoneNumbers.first().normalizedNumber, contact.name)
                }
            }.apply {
                binding.contactsList.adapter = this
            }

            if (areSystemAnimationsEnabled) {
                binding.contactsList.scheduleLayoutAnimation()
            }
        } else {
            (currAdapter as ContactsAdapter).updateContacts(contacts)
        }

        setupLetterFastscroller(contacts)
    }

    private fun fillSuggestedContacts(callback: () -> Unit) {
        val privateCursor = getMyContactsCursor(false, true)
        ensureBackgroundThread {
            privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val suggestions = getSuggestedContacts(privateContacts)
            runOnUiThread {
                binding.suggestionsHolder.removeAllViews()
                if (suggestions.isEmpty()) {
                    binding.suggestionsLabel.beGone()
                    binding.suggestionsScrollview.beGone()
                } else {
                    binding.suggestionsLabel.beVisible()
                    binding.suggestionsScrollview.beVisible()
                    suggestions.forEach {
                        val contact = it
                        ItemSuggestedContactBinding.inflate(layoutInflater).apply {
                            suggestedContactName.text = contact.name
                            suggestedContactName.setTextColor(getProperTextColor())

                            if (!isDestroyed) {
                                SimpleContactsHelper(this@NewConversationActivity).loadContactImage(
                                    contact.photoUri,
                                    suggestedContactImage,
                                    contact.name
                                )
                                binding.suggestionsHolder.addView(root)
                                root.setOnClickListener {
                                    launchThreadActivity(
                                        contact.phoneNumbers.first().normalizedNumber,
                                        contact.name
                                    )
                                }
                            }
                        }
                    }
                }
                callback()
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        binding.contactsLetterFastscroller.setupWithRecyclerView(binding.contactsList, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(
                    character.uppercase(Locale.getDefault()).normalizeString()
                )
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    private fun launchThreadActivity(phoneNumber: String, name: String) {
        hideKeyboard()
        val text =
            intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra("sms_body") ?: ""
        val numbers = phoneNumber.split(";").toSet()
        val number = if (numbers.size == 1) phoneNumber else Gson().toJson(numbers)
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, getThreadId(numbers))
            putExtra(THREAD_TITLE, name)
            putExtra(THREAD_TEXT, text)
            putExtra(THREAD_NUMBER, number)

            if (intent.action == Intent.ACTION_SEND && intent.extras?.containsKey(Intent.EXTRA_STREAM) == true) {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URI, uri?.toString())
            } else if (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.extras?.containsKey(
                    Intent.EXTRA_STREAM
                ) == true
            ) {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                putExtra(THREAD_ATTACHMENT_URIS, uris)
            }

            startActivity(this)
        }
    }
}
