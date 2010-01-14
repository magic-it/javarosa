/*
 * Copyright (C) 2009 JavaRosa
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.javarosa.formmanager.view.singlequestionscreen;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Gauge;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.ItemCommandListener;
import javax.microedition.lcdui.List;

import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.services.UnavailableServiceException;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.formmanager.controller.FormEntryController;
import org.javarosa.formmanager.model.FormEntryModel;
import org.javarosa.formmanager.utility.FormEntryModelListener;
import org.javarosa.formmanager.view.FormElementBinding;
import org.javarosa.formmanager.view.IFormEntryView;
import org.javarosa.formmanager.view.singlequestionscreen.acquire.AcquireScreen;
import org.javarosa.formmanager.view.singlequestionscreen.acquire.AcquiringQuestionScreen;
import org.javarosa.formmanager.view.singlequestionscreen.acquire.IAcquiringService;
import org.javarosa.formmanager.view.singlequestionscreen.screen.SingleQuestionScreen;
import org.javarosa.formmanager.view.singlequestionscreen.screen.SingleQuestionScreenFactory;
import org.javarosa.j2me.view.J2MEDisplay;

public class SingleQuestionScreenManager implements IFormEntryView,
		FormEntryModelListener, CommandListener, ItemCommandListener {
	private FormEntryController controller;
	private FormEntryModel model;

	private FormIndex index;
	private FormElementBinding prompt;
	private IAnswerData answer;
	private SingleQuestionScreen currentQuestionScreen;
	Gauge progressBar;
	private boolean showFormView;
	private boolean goingForward;
	private FormViewScreen formView;

	// GUI elements
	public SingleQuestionScreenManager(String formTitle, FormEntryModel model,
			FormEntryController controller) {
		this.model = model;
		this.controller = controller;
		this.showFormView = true;
		model.registerObservable(this);
		// immediately setup question, need to decide if this is the best place
		// to do it
		// this.getView(questionIndex);
		controller.setFormEntryView(this);

	}

	public FormIndex getIndex() {
		index = model.getQuestionIndex();// return index of active question
		return index;
	}

	public void getView(FormIndex qIndex, boolean fromFormView) {
		prompt = new FormElementBinding(null, qIndex, model
				.getForm());
		if (((QuestionDef) prompt.element).getControlType() == Constants.DATATYPE_BARCODE) {
			try { // is there a service that can acquire a barcode?
				IAcquiringService barcodeService = (IAcquiringService) controller
						.getDataCaptureService("clforms-barcode");

				currentQuestionScreen = SingleQuestionScreenFactory.getQuestionScreen(
						prompt, fromFormView, goingForward, barcodeService);

			} catch (UnavailableServiceException se) {
				//otherwise just get whatever else can handle the question type
				currentQuestionScreen = SingleQuestionScreenFactory.getQuestionScreen(
						prompt, fromFormView, goingForward);
			}

		} else {
			currentQuestionScreen = SingleQuestionScreenFactory.getQuestionScreen(prompt,
					fromFormView, goingForward);
		}
		
		currentQuestionScreen.setCommandListener(this);
		currentQuestionScreen.setItemCommandListner(this);
		controller.setView(currentQuestionScreen);
	}

	public void destroy() {
		model.unregisterObservable(this);
	}

	public void show() {
		if (this.showFormView)
			showFormViewScreen();
		else
			getView(getIndex(), this.showFormView);// refresh view
	}

	private void showFormViewScreen() {
		model.setQuestionIndex(FormIndex.createBeginningOfFormIndex());
		formView = new FormViewScreen(this.model);
		formView.setCommandListener(this);
		controller.setView(formView);
	}

	public void refreshView() {
		getView(getIndex(), this.showFormView);// refresh view
	}

	public void formComplete() {
		if (!model.isReadOnly()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ie) {
			}

			controller.save();// always save form
			controller.exit();
		}
	}

	public void questionIndexChanged(FormIndex questionIndex) {
		if (questionIndex.isInForm())
			getView(getIndex(), this.showFormView);// refresh view
	}

	public void saveStateChanged(int instanceID, boolean dirty) {
		// TODO Auto-generated method stub

	}

	public void commandAction(Command command, Displayable arg1) {
		if (arg1 == formView) {
			if (command == FormViewScreen.backCommand) {
				this.show();
			} else if (command == FormViewScreen.exitNoSaveCommand) {
				controller.exit();
			} else if (command == FormViewScreen.exitSaveCommand) {
				controller.save();
				controller.exit();
			} else if (command == FormViewScreen.sendCommand) {
				// check if all required questions are complete
				int counter = model.countUnansweredQuestions(true);

				if (counter > 0) {
					// show alert
					String txt = "There are unanswered compulsory questions and must be completed first to proceed";
					J2MEDisplay.showError("Question Required!", txt);
				} else
					model.setFormComplete();
				// controller.exit();
			} else if (command == List.SELECT_COMMAND) {
				if (!model.isReadOnly()) {
					int i = formView.getSelectedIndex();
					FormIndex b = formView.indexHash.get(i);
					controller.selectQuestion(b);
					this.showFormView = false;
					this.goingForward = true;
				} else {
					String txt = Localization
							.get("view.sending.FormUneditable");
					J2MEDisplay.showError("Cannot Edit Answers!", txt);
				}
			}

		} else {
			if (command == SingleQuestionScreen.nextItemCommand
					|| command == SingleQuestionScreen.nextCommand) {
				answer = currentQuestionScreen.getWidgetValue();

				this.goingForward = true;
				int result = controller.questionAnswered(this.prompt,
						this.answer);
				if (result == FormEntryController.QUESTION_CONSTRAINT_VIOLATED) {
					// System.out.println("answer validation constraint violated");
					// TODO: String txt = Locale.get(
					// "view.sending.CompulsoryQuestionsIncomplete");

					String txt = "Validation failure: data is not of the correct format.";

					J2MEDisplay.showError("Question Required!", txt);
				} else if (result == FormEntryController.QUESTION_REQUIRED_BUT_EMPTY) {
					// String txt = Locale.get(
					// "view.sending.CompulsoryQuestionsIncomplete");
					String txt = "This question is compulsory. You must answer it.";
					J2MEDisplay.showError("Question Required!", txt);
				}

			} else if (command == SingleQuestionScreen.previousCommand) {
				this.goingForward = false;
				controller.stepQuestion(false);

			} else if (command == SingleQuestionScreen.viewAnswersCommand) {
				controller.selectQuestion(FormIndex
						.createBeginningOfFormIndex());
				this.showFormView = true;
				showFormViewScreen();
			} else if (command == SingleQuestionScreen.viewAnswersCommand) {
				controller.selectQuestion(FormIndex
						.createBeginningOfFormIndex());
				this.showFormView = true;
				showFormViewScreen();
			} else if ((arg1 instanceof AcquireScreen)) {
				// handle additional commands for acquring screens
				AcquireScreen source = (AcquireScreen) arg1;
				System.out.println("Got event from AcquireScreen");
				if (command == source.cancelCommand) {
					AcquiringQuestionScreen questionScreen = source
							.getQuestionScreen();
					questionScreen.setCommandListener(this);
					controller.setView(questionScreen);
				}
			} else if (arg1 instanceof AcquiringQuestionScreen) {
				// handle additional commands for acquring question screens
				AcquiringQuestionScreen aqQuestionScreen = (AcquiringQuestionScreen) arg1;
				System.out
						.println("Got event from AcquiringSingleQuestionScreen");
				if (command == aqQuestionScreen.acquireCommand) {
					controller.setView(aqQuestionScreen.getAcquireScreen(this));
				}
			}

		}
	}

	public void commandAction(Command c, Item item) {
		if (c == SingleQuestionScreen.nextItemCommand) {
			answer = currentQuestionScreen.getWidgetValue();
			controller.questionAnswered(this.prompt, answer);// store answers
			refreshView();

		}
	}

	public boolean isShowOverView() {
		return showFormView;
	}

	public void setShowOverView(boolean showOverView) {
		this.showFormView = showOverView;
	}

	public void startOfForm() {
		// TODO Auto-generated method stub

	}
}