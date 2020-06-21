describe('Time sheets for other users test', () => {
    beforeEach(() => {
        return cy.setup().then(() => cy.login());
    });

    it('Should be able to add a new time entry', () => {
        cy.visit('/users');
        cy.get('.users-list__item .uk-button').contains('time sheet', {matchCase: false}).first().click();
        cy.url().should('match', /\/users\/\d+\/entries/);

        cy.get('.home a.uk-button-primary')
            .contains('New Entry', {matchCase: false})
            .should('have.length', 1)
            .click();

        cy.get('#Description').type('some description');
        cy.get('[id="Duration - Hours"]').type('5');
        cy.get('[id="Duration - Minutes"]').type('15');
        cy.get('#start-date').click();
        cy.get('.react-datepicker__day').first().click();

        cy.get('form').submit();

        cy.url().should('match', /\/users\/\d+\/entries/);
        cy.get('.time-entry').should('have.length', 1);
    });

    it('should be able to edit an entry', () => {
        cy.visit('/users');
        cy.get('.users-list__item .uk-button').contains('time sheet', {matchCase: false}).first().click();
        cy.url().should('match', /\/users\/\d+\/entries/);

        cy.get('.time-entry .uk-button-default').click();

        cy.get('#Description').type(' - Edited');

        cy.get('form').submit();

        cy.url().should('match', /\/users\/\d+\/entries/);
        cy.get('.time-entry')
            .should('have.length', 1)
            .and('contain.text', 'Edited');
    });

    it('should be able to delete an entry', () => {
        cy.visit('/users');
        cy.get('.users-list__item .uk-button').contains('time sheet', {matchCase: false}).first().click();
        cy.url().should('match', /\/users\/\d+\/entries/);

        cy.get('.time-entry .uk-button-danger').click();
        cy.get('.time-entry').should('have.length', 0);
    });
})