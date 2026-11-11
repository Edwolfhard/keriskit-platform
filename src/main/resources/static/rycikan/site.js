(() => {
    "use strict";

    /* ========================================
       MOBILE NAVIGATION
    ======================================== */

    const menuToggle = document.getElementById("menu-toggle");
    const navigation = document.getElementById("navlinks");

    if (menuToggle && navigation) {
        function closeMenu() {
            navigation.classList.remove("open");
            menuToggle.setAttribute("aria-expanded", "false");
            menuToggle.setAttribute(
                "aria-label",
                "Open navigation menu"
            );
        }

        menuToggle.addEventListener("click", () => {
            const isOpen =
                menuToggle.getAttribute("aria-expanded") !== "true";

            navigation.classList.toggle("open", isOpen);
            menuToggle.setAttribute("aria-expanded", String(isOpen));
            menuToggle.setAttribute(
                "aria-label",
                isOpen
                    ? "Close navigation menu"
                    : "Open navigation menu"
            );
        });

        navigation.querySelectorAll("a").forEach(link => {
            link.addEventListener("click", closeMenu);
        });

        document.addEventListener("keydown", event => {
            if (
                event.key === "Escape" &&
                navigation.classList.contains("open")
            ) {
                closeMenu();
                menuToggle.focus();
            }
        });

        window.matchMedia("(min-width: 801px)")
            .addEventListener("change", closeMenu);
    }

    /* ========================================
       KERISBOT
    ======================================== */

    function initialiseChatbot() {
        const chatWindow = document.getElementById("chat-window");
        const chatButton = document.getElementById("chat-fab");
        const closeButton = document.getElementById("chat-close");
        const chatForm = document.getElementById("chat-form");
        const input = document.getElementById("chat-input");
        const sendButton = document.getElementById("chat-send");
        const chatLog = document.getElementById("chat-log");
        const statusText = document.getElementById("chat-status");

        const statusDot =
            document.getElementById("chat-status-dot") ||
            document.getElementById("chat-dot");

        const homeChatTools =
            document.getElementById("home-chat-tools");

        if (
            !chatWindow || !chatButton || !closeButton ||
            !chatForm || !input || !sendButton || !chatLog
        ) {
            return;
        }

        const conversationHistory = [];
        const MAX_HISTORY_MESSAGES = 12;
        const MAX_HISTORY_CHARACTERS = 12000;
        const MAX_HISTORY_MESSAGE_LENGTH = 2000;

        let isSending = false;
        let returnFocus = chatButton;

        input.placeholder = "Ask me about the keris…";

        chatWindow.querySelectorAll(".chat-hint")
            .forEach(hint => {
                hint.textContent =
                    "Enter to send · Shift + Enter for a new line";
            });

        function openChat(trigger = chatButton) {
            if (chatWindow.hidden) {
                returnFocus = chatWindow.contains(trigger)
                    ? chatButton
                    : trigger;
            }

            if (homeChatTools) {
                homeChatTools.hidden = false;
            }

            chatWindow.hidden = false;
            chatButton.setAttribute("aria-expanded", "true");
            input.focus();
        }

        function closeChat() {
            chatWindow.hidden = true;
            chatButton.setAttribute("aria-expanded", "false");

            if (returnFocus && returnFocus.isConnected) {
                returnFocus.focus();
            } else {
                chatButton.focus();
            }
        }

        function setStatus(text, state = "") {
            if (statusText) {
                statusText.textContent = text;
            }

            if (statusDot) {
                statusDot.classList.remove("ready", "error");

                if (state) {
                    statusDot.classList.add(state);
                }
            }
        }

        function scrollToLatestMessage() {
            chatLog.scrollTop = chatLog.scrollHeight;
        }

        function cleanBotText(text) {
            return text
                .replace(/\[S\d+\]/g, "")
                .replace(/[ \t]+([.,!?;:])/g, "$1")
                .replace(/[ \t]{2,}/g, " ")
                .trim();
        }

        function rememberExchange(question, answer) {
            conversationHistory.push(
                {
                    role: "user",
                    content: question
                },
                {
                    role: "assistant",
                    content: cleanBotText(answer)
                        .slice(0, MAX_HISTORY_MESSAGE_LENGTH)
                }
            );

            // Remove complete exchanges, keeping recent turns together.
            while (
                conversationHistory.length > MAX_HISTORY_MESSAGES ||
                conversationHistory.reduce(
                    (total, message) => total + message.content.length,
                    0
                ) > MAX_HISTORY_CHARACTERS
                ) {
                conversationHistory.splice(0, 2);
            }
        }

        function appendMessage(text, type = "bot") {
            const message = document.createElement("div");

            message.className = type === "user"
                ? "message user"
                : "message";

            const displayText = type === "user"
                ? text
                : cleanBotText(text);

            // Never render model output as executable HTML.
            message.textContent = displayText;

            chatLog.appendChild(message);
            scrollToLatestMessage();

            return message;
        }

        function setSending(sending) {
            isSending = sending;
            sendButton.disabled = sending;
            chatLog.setAttribute("aria-busy", String(sending));

            chatWindow.querySelectorAll("[data-question]")
                .forEach(button => {
                    button.disabled = sending;
                });
        }

        async function sendQuestion(question) {
            if (isSending) {
                return;
            }

            const trimmedQuestion = question.trim();

            if (
                trimmedQuestion.length === 0 ||
                trimmedQuestion.length > 500
            ) {
                input.setCustomValidity(
                    "Enter a question between 1 and 500 characters."
                );

                input.reportValidity();
                input.focus();
                return;
            }

            input.setCustomValidity("");
            setSending(true);

            appendMessage(trimmedQuestion, "user");
            input.value = "";

            setStatus("Thinking…");

            const waitingMessage = appendMessage(
                "Let me look into that…"
            );

            const controller = new AbortController();

            const timeout = window.setTimeout(() => {
                controller.abort();
            }, 60000);

            try {
                const response = await fetch("/api/chatbot/chat", {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json"
                    },
                    body: JSON.stringify({
                        question: trimmedQuestion,
                        history: conversationHistory.map(message => ({
                            role: message.role,
                            content: message.content
                        }))
                    }),
                    signal: controller.signal
                });

                if (!response.ok) {
                    throw new Error("Chat service unavailable.");
                }

                const data = await response.json();

                if (
                    !data ||
                    typeof data.answer !== "string" ||
                    data.answer.trim().length === 0
                ) {
                    throw new Error("Empty chat response.");
                }

                waitingMessage.remove();

                // Source metadata is intentionally not displayed.
                appendMessage(data.answer.trim(), "bot");

                // Save only completed exchanges after a valid response.
                rememberExchange(
                    trimmedQuestion,
                    data.answer.trim()
                );

                setStatus("Ready to chat", "ready");
            } catch (error) {
                waitingMessage.remove();

                appendMessage(
                    error.name === "AbortError"
                        ? "That’s taking longer than expected. Please try again with a shorter question."
                        : "I’m having trouble connecting right now. Please try again in a moment."
                );

                setStatus("Connection unavailable", "error");

                // Preserve any new text entered while waiting.
                if (input.value.trim().length === 0) {
                    input.value = trimmedQuestion;
                }
            } finally {
                window.clearTimeout(timeout);
                setSending(false);
                scrollToLatestMessage();
            }
        }

        chatButton.addEventListener("click", () => {
            if (chatWindow.hidden) {
                openChat(chatButton);
            } else {
                closeChat();
            }
        });

        closeButton.addEventListener("click", closeChat);

        chatWindow.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                event.stopPropagation();
                closeChat();
            }
        });

        document.querySelectorAll("[data-open-chat]")
            .forEach(button => {
                button.addEventListener("click", () => {
                    openChat(button);
                });
            });

        document.querySelectorAll("[data-question]")
            .forEach(button => {
                button.addEventListener("click", () => {
                    const question = button.dataset.question;

                    if (!question) {
                        return;
                    }

                    openChat(button);
                    sendQuestion(question);
                });
            });

        chatForm.addEventListener("submit", event => {
            event.preventDefault();
            sendQuestion(input.value);
        });

        input.addEventListener("input", () => {
            input.setCustomValidity("");
        });

        input.addEventListener("keydown", event => {
            if (
                event.key === "Enter" &&
                !event.shiftKey &&
                !event.isComposing
            ) {
                event.preventDefault();
                sendQuestion(input.value);
            }
        });
    }

    initialiseChatbot();

    /* ========================================
       OPTIONAL CONTACT FORM
       Runs only if the form exists.
       Prepares a download; does not send email.
    ======================================== */

    function initialiseContactForm() {
        const form = document.getElementById("contact-form");
        const result = document.getElementById("form-result");

        const downloadButton =
            document.getElementById("download-message");

        if (!form || !result || !downloadButton) {
            return;
        }

        const fieldDefinitions = [
            { id: "name", label: "name", limit: 100 },
            { id: "email", label: "email address", limit: 254 },
            { id: "subject", label: "subject", limit: 150 },
            { id: "message", label: "message", limit: 3000 }
        ];

        const fields = fieldDefinitions.map(definition => ({
            ...definition,
            element: document.getElementById(definition.id),
            error: document.getElementById(
                `${definition.id}-error`
            )
        }));

        if (fields.some(field => !field.element || !field.error)) {
            return;
        }

        let preparedMessage = null;

        function validateField(field) {
            const value = field.element.value.trim();
            let errorMessage = "";

            if (!value) {
                errorMessage = `Please enter your ${field.label}.`;
            } else if (value.length > field.limit) {
                errorMessage =
                    `Use no more than ${field.limit} characters.`;
            } else if (
                field.id === "email" &&
                field.element.validity.typeMismatch
            ) {
                errorMessage = "Please enter a valid email address.";
            }

            field.error.textContent = errorMessage;

            field.element.setAttribute(
                "aria-invalid",
                String(Boolean(errorMessage))
            );

            return errorMessage.length === 0;
        }

        fields.forEach(field => {
            field.element.addEventListener("blur", () => {
                validateField(field);
            });

            field.element.addEventListener("input", () => {
                preparedMessage = null;
                result.hidden = true;

                if (
                    field.element.getAttribute("aria-invalid") === "true"
                ) {
                    validateField(field);
                }
            });
        });

        form.addEventListener("submit", event => {
            event.preventDefault();

            const validationResults = fields.map(validateField);
            const firstInvalidIndex =
                validationResults.indexOf(false);

            if (firstInvalidIndex !== -1) {
                preparedMessage = null;
                result.hidden = true;
                fields[firstInvalidIndex].element.focus();
                return;
            }

            preparedMessage = Object.fromEntries(
                fields.map(field => [
                    field.id,
                    field.element.value.trim()
                ])
            );

            result.hidden = false;
        });

        downloadButton.addEventListener("click", () => {
            if (!preparedMessage) {
                return;
            }

            const content = [
                "RYCIKĀN ENQUIRY — NOT SENT",
                "",
                `Name: ${preparedMessage.name}`,
                `Email: ${preparedMessage.email}`,
                `Subject: ${preparedMessage.subject}`,
                "",
                preparedMessage.message
            ].join("\n");

            const file = new Blob([content], {
                type: "text/plain;charset=utf-8"
            });

            const fileUrl = URL.createObjectURL(file);
            const downloadLink = document.createElement("a");

            downloadLink.href = fileUrl;
            downloadLink.download = "rycikan-enquiry.txt";

            document.body.appendChild(downloadLink);
            downloadLink.click();
            downloadLink.remove();

            window.setTimeout(() => {
                URL.revokeObjectURL(fileUrl);
            }, 1000);
        });
    }

    initialiseContactForm();
})();